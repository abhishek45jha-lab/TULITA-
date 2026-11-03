package tulita.budget.controller;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import tulita.budget.dao.BudgetHistoryDao;
import tulita.budget.dao.JsonBudgetHistoryDao;
import tulita.budget.engine.OptimizationConstraintValidator;
import tulita.budget.model.BudgetRecommendation;
import tulita.budget.model.BudgetRequest;
import tulita.budget.model.OptimizationResult;
import tulita.budget.model.RecommendationDelta;
import tulita.budget.model.SavedBudgetRun;
import tulita.budget.model.WhatIfOption;
import tulita.budget.model.SelectedProduct;
import tulita.budget.service.BudgetExportService;
import tulita.budget.service.BudgetMoney;
import tulita.budget.service.BudgetOptimizerService;
import tulita.budget.view.BudgetOptimizerView;
import tulita.photocatalog.model.PhotoProduct;

import java.io.File;
import java.nio.file.Path;
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Drives the budget planner: loads the shared catalogue, runs the optimiser off the JavaFX thread, and
 * publishes results back on it.
 *
 * <p>The controller never loads products itself; the supplier comes from the comparison module, so the
 * planner always works on the same validated catalogue (local JSON or MySQL) as the rest of the app.</p>
 */
public final class BudgetOptimizerController implements AutoCloseable {

    private final BudgetOptimizerView view;
    private final Supplier<List<PhotoProduct>> catalogue;
    private final Path catalogueRoot;
    private final BudgetOptimizerService service = new BudgetOptimizerService();
    private final BudgetExportService exportService = new BudgetExportService();
    private final BudgetHistoryDao history;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tulita-budget-worker");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean closed;
    private volatile boolean loaded;
    private volatile BudgetRequest lastRequest;
    private volatile OptimizationResult lastResult;

    public BudgetOptimizerController(BudgetOptimizerView view, Supplier<List<PhotoProduct>> catalogue,
                                     Path catalogueRoot) {
        this(view, catalogue, catalogueRoot, JsonBudgetHistoryDao.defaultStore());
    }

    public BudgetOptimizerController(BudgetOptimizerView view, Supplier<List<PhotoProduct>> catalogue,
                                     Path catalogueRoot, BudgetHistoryDao history) {
        this.view = view;
        this.catalogue = catalogue;
        this.catalogueRoot = catalogueRoot;
        this.history = history;
        view.onOptimize(this::optimize);
        view.onSave(this::save);
        view.onExport(this::export);
        view.onReset(() -> {
            view.reset();
            lastResult = null;
            lastRequest = null;
        });
        view.onOpenLink(this::openLink);
        view.onWhatIf(option -> {
            if (option == null) {
                return;
            }
            view.applyRequest(option.request());
            view.status("Applied \"" + option.label() + "\" — re-optimising.");
            optimize();
        });
    }

    /** Opens a recorded retailer page in the system browser; nothing is fetched by Tulita itself. */
    private void openLink(String url) {
        if (url == null || url.isBlank()) {
            view.status("This observation has no recorded retailer link.");
            return;
        }
        try {
            if (opener != null) {
                opener.accept(url);
            } else if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                view.status("No browser is available here. The recorded link is: " + url);
            }
        } catch (Exception e) {
            view.status("Could not open the stored link (" + url + "): " + message(e));
        }
    }

    private Consumer<String> opener;

    /** Lets the embedding view supply its own link opener; defaults to the system browser. */
    public void onOpenLink(Consumer<String> opener) {
        this.opener = opener;
    }

    /** Called when the planner page is opened for the first time. */
    public void activate() {
        if (loaded) {
            return;
        }
        view.status("Reading the catalogue…");
        worker.submit(() -> {
            try {
                List<PhotoProduct> products = catalogue.get();
                String currency = service.currency(products);
                List<SavedBudgetRun> saved = safeHistory();
                Platform.runLater(() -> {
                    if (closed) {
                        return;
                    }
                    view.loadCatalogue(products, currency, catalogueRoot);
                    view.showSaved(saved);
                    loaded = true;
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!closed) {
                        view.showError("The catalogue could not be read for the planner. "
                            + message(e));
                    }
                });
            }
        });
    }

    public void reload() {
        loaded = false;
        activate();
    }

    private void optimize() {
        BudgetRequest request;
        try {
            request = view.readRequest();
        } catch (IllegalArgumentException e) {
            view.showError(e.getMessage());
            return;
        }
        lastRequest = request;
        view.busy(true);
        view.status("Searching the catalogue within " + view.describeBudget(request) + "…");
        List<PhotoProduct> products = catalogue.get();
        OptimizationResult previous = lastResult;
        worker.submit(() -> {
            try {
                OptimizationResult result = service.optimize(products, request);
                List<WhatIfOption> options = service.whatIfOptions(request);
                RecommendationDelta delta = previous == null || previous.primary() == null
                    ? null : service.delta(previous.primary(), result.primary());
                Platform.runLater(() -> {
                    if (closed) {
                        return;
                    }
                    lastResult = result;
                    view.showResult(result, request, delta, options);
                });
            } catch (OptimizationConstraintValidator.InvalidRequestException e) {
                Platform.runLater(() -> {
                    if (!closed) {
                        view.showError(String.join(" ", e.problems()));
                    }
                });
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    if (!closed) {
                        view.showError("The optimiser could not complete: " + message(e));
                    }
                });
            }
        });
    }

    private void save() {
        OptimizationResult result = lastResult;
        BudgetRequest request = lastRequest;
        if (result == null || result.primary() == null || request == null) {
            view.showError("Optimise a plan first, then save it.");
            return;
        }
        SavedBudgetRun run = toSavedRun(result.primary(), request, result);
        worker.submit(() -> {
            try {
                history.save(run);
                List<SavedBudgetRun> saved = safeHistory();
                Platform.runLater(() -> {
                    if (!closed) {
                        view.showSaved(saved);
                        view.status("Saved to " + history.location() + " (JSON, not inside the catalogue).");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!closed) {
                        view.showError("Could not save the recommendation: " + message(e));
                    }
                });
            }
        });
    }

    private void export() {
        OptimizationResult result = lastResult;
        BudgetRequest request = lastRequest;
        if (result == null || result.primary() == null || request == null) {
            view.showError("Optimise a plan first, then export it.");
            return;
        }
        Window owner = view.getScene() == null ? null : view.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export the recommendation as CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV report", "*.csv"));
        chooser.setInitialFileName("tulita-budget-plan.csv");
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        Path path = target.toPath();
        worker.submit(() -> {
            try {
                exportService.export(result, request, path);
                Platform.runLater(() -> {
                    if (!closed) {
                        view.status("Exported the plan on screen to " + path + ".");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!closed) {
                        view.showError("Could not export the CSV: " + message(e));
                    }
                });
            }
        });
    }

    private SavedBudgetRun toSavedRun(BudgetRecommendation recommendation, BudgetRequest request,
                                      OptimizationResult result) {
        List<SavedBudgetRun.Line> lines = new ArrayList<>();
        for (SelectedProduct line : recommendation.selected()) {
            lines.add(new SavedBudgetRun.Line(line.candidate().category(), line.candidate().productId(),
                line.candidate().name(), line.candidate().store(), line.quantity(),
                BudgetMoney.scale(line.candidate().unitPrice()), line.lineTotal(), line.score().total(),
                line.candidate().observedAt()));
        }
        List<String> notes = new ArrayList<>(recommendation.limitations());
        notes.addAll(result.limitations());
        return SavedBudgetRun.of(recommendation.mode().label(), request.currency(),
            recommendation.breakdown().budget(), recommendation.breakdown().itemsSubtotal(),
            recommendation.breakdown().deliveryCharges(), recommendation.breakdown().discount(),
            recommendation.breakdown().total(), recommendation.breakdown().remaining(),
            request.weights().describe(), lines, notes);
    }

    private List<SavedBudgetRun> safeHistory() {
        try {
            return history.list();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** The recommendation currently on screen (null before the first run). */
    public OptimizationResult lastResult() {
        return lastResult;
    }

    /** The request that produced it. */
    public BudgetRequest lastRequest() {
        return lastRequest;
    }

    /** Test hook: run one optimisation synchronously without the UI thread. */
    public OptimizationResult optimizeNow(List<PhotoProduct> products, BudgetRequest request) {
        return service.optimize(products, request);
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
        try {
            worker.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
