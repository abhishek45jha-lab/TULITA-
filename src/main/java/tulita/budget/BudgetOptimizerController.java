package tulita.budget;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import tulita.budget.model.BudgetAlternative;
import tulita.budget.model.BudgetPlan;
import tulita.budget.model.BudgetRequest;
import tulita.budget.service.BudgetCsvExporter;
import tulita.budget.service.BudgetDataLoader;
import tulita.budget.service.BudgetOptimizer;
import tulita.budget.view.BudgetOptimizerView;
import tulita.dao.DataAccessException;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Coordinates catalogue loading, background optimisation and CSV export for the budget screen. */
public final class BudgetOptimizerController implements AutoCloseable {
    private final BudgetOptimizerView view;
    private final boolean multi;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tulita-budget-worker");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> pending;
    private volatile BudgetDataLoader.Loaded loaded;
    private BudgetPlan currentPlan;
    private BudgetRequest currentRequest;
    private volatile boolean closed;

    public BudgetOptimizerController(BudgetOptimizerView view, boolean multi) {
        this.view = view;
        this.multi = multi;
        view.setActions(new BudgetOptimizerView.Actions() {
            @Override public void optimize(BudgetRequest request) { runOptimize(request); }
            @Override public void reload() { load(); }
            @Override public void export(BudgetPlan plan) { chooseExport(plan); }
            @Override public void applyAlternative(BudgetAlternative alternative) { apply(alternative); }
        });
    }

    public void load() {
        if (closed) return;
        if (pending != null && !pending.isDone()) pending.cancel(true);
        currentPlan = null;
        currentRequest = null;
        view.showLoadingData();
        pending = worker.submit(() -> {
            try {
                BudgetDataLoader.Loaded result = BudgetDataLoader.load(multi);
                Platform.runLater(() -> {
                    if (closed) return;
                    loaded = result;
                    view.loaded(result);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (closed) return;
                    view.showError(message(ex));
                });
            }
        });
    }

    private void runOptimize(BudgetRequest request) {
        if (loaded == null || closed) {
            view.setFormError("The catalogue is not ready yet.");
            return;
        }
        currentRequest = request;
        view.setBusy(true);
        view.optimizing();
        pending = worker.submit(() -> {
            try {
                BudgetPlan plan = new BudgetOptimizer().optimize(request, loaded.catalog());
                Platform.runLater(() -> {
                    if (closed) return;
                    currentPlan = plan;
                    view.showPlan(plan);
                    view.setBusy(false);
                });
            } catch (IllegalArgumentException ex) {
                Platform.runLater(() -> {
                    if (closed) return;
                    view.setFormError(ex.getMessage());
                    view.setBusy(false);
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (closed) return;
                    view.setFormError("The plan could not be calculated: " + ex.getMessage());
                    view.setBusy(false);
                });
            }
        });
    }

    private void apply(BudgetAlternative alternative) {
        if (currentPlan == null || currentRequest == null) return;
        try {
            BudgetPlan applied = BudgetPlan.fromAlternative(alternative, currentPlan.budget(),
                    currentPlan.warnings(), currentPlan.explanations());
            currentPlan = applied;
            view.showPlan(applied);
        } catch (RuntimeException ex) {
            alert(Alert.AlertType.ERROR, "Could not apply the alternative", ex.getMessage());
        }
    }

    private void chooseExport(BudgetPlan plan) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export budget plan as CSV");
        chooser.setInitialFileName("tulita-budget-plan.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        Window owner = view.getScene() == null ? null : view.getScene().getWindow();
        File target = chooser.showSaveDialog(owner);
        if (target == null) return;
        Path path = target.toPath();
        if (!path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv"))
            path = path.resolveSibling(path.getFileName() + ".csv");
        Path finalPath = path;
        view.setBusy(true);
        worker.submit(() -> {
            try {
                BudgetCsvExporter.export(finalPath, plan, loaded);
                Platform.runLater(() -> {
                    view.setBusy(false);
                    alert(Alert.AlertType.INFORMATION, "Budget plan exported",
                            "Saved to " + finalPath.toAbsolutePath() + "\nSummary, products, reasons and alternatives are included.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    view.setBusy(false);
                    alert(Alert.AlertType.ERROR, "Could not export the plan", message(ex));
                });
            }
        });
    }

    private static String message(Exception ex) {
        if (ex instanceof DataAccessException) {
            return ex.getMessage() + "\n" + BudgetDataLoader.databaseHint();
        }
        String text = ex.getMessage();
        return (text == null || text.isBlank() ? ex.getClass().getSimpleName() : text)
                + "\n" + BudgetDataLoader.databaseHint();
    }

    private static void alert(Alert.AlertType type, String title, String body) {
        Alert alert = new Alert(type, body == null ? "" : body);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    @Override public void close() {
        closed = true;
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
    }
}
