package tulita.budget;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tulita.budget.controller.BudgetOptimizerController;
import tulita.budget.dao.JsonBudgetHistoryDao;
import tulita.budget.model.SavedBudgetRun;
import tulita.budget.model.OptimizationResult;
import tulita.budget.model.RecommendationMode;
import tulita.budget.view.BudgetOptimizerView;
import tulita.photocatalog.PhotoCatalogController;
import tulita.photocatalog.dao.JsonPhotoCatalogDao;
import tulita.photocatalog.view.PhotoCatalogView;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.nio.file.Files;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check of the budget planner inside the real application window.
 *
 * <p>Opt-in, because it needs a display (or Xvfb):
 * {@code mvn test -Dtulita.budgetUiTests=true -Dtulita.screenshots=true -Dtest=BudgetOptimizerUiTest}</p>
 */
class BudgetOptimizerUiTest {

    private Stage stage;
    private PhotoCatalogView catalogView;
    private PhotoCatalogController catalogController;
    private BudgetOptimizerView budgetView;
    private BudgetOptimizerController budgetController;

    private <T> T fx(Callable<T> call) throws Exception {
        FutureTask<T> task = new FutureTask<>(call);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    private void until(Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (!fx(condition)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError(message);
            }
            Thread.sleep(100);
        }
    }

    @Test
    void plannerProducesARecommendationInsideTheApplication() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.budgetUiTests"),
            "opt-in: needs a display, enable with -Dtulita.budgetUiTests=true");
        Path root = JsonPhotoCatalogDao.defaultRoot();
        Path history = Files.createTempDirectory("tulita-ui-history");
        Path historyFile = history.resolve("recommendations.json");
        Assumptions.assumeTrue(Files.isDirectory(root), "curated catalogue not present");

        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.complete(null);
        });
        ready.get(10, TimeUnit.SECONDS);

        try {
            fx(() -> {
                catalogView = new PhotoCatalogView();
                budgetView = new BudgetOptimizerView();
                stage = new Stage();
                Scene scene = new Scene(catalogView, 1400, 1040);
                scene.getStylesheets().add(getClass().getResource("/styles/tulita.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                catalogController = new PhotoCatalogController(catalogView, stage, url -> { });
                budgetController = new BudgetOptimizerController(budgetView, catalogController::products,
                    catalogController.catalogRoot(), new JsonBudgetHistoryDao(historyFile));
                catalogView.onBudgetOpen(() -> {
                    catalogView.showBudgetPage(budgetView);
                    budgetController.activate();
                });
                catalogController.load();
                return null;
            });
            until(() -> ((Label) catalogView.lookup("#catalog-count")).getText().contains("79 results"),
                "the catalogue did not load");

            // navigate through the real rail button, exactly as a user would
            fx(() -> {
                ((Button) catalogView.lookup("#nav-budget-planner")).fire();
                return null;
            });
            until(() -> catalogView.showingBudgetPage()
                && budgetView.lookup("#budget-required-computers") != null, "budget page did not open");

            // a budget that can satisfy Computers + Kitchen & Appliances
            fx(() -> {
                ((TextField) budgetView.lookup("#budget-amount")).setText("2500");
                ((CheckBox) budgetView.lookup("#budget-required-computers")).setSelected(true);
                ((CheckBox) budgetView.lookup("#budget-required-kitchen-appliances")).setSelected(true);
                ((Button) budgetView.lookup("#budget-optimize")).fire();
                return null;
            });
            until(() -> budgetController.lastResult() != null, "the optimiser never returned");

            OptimizationResult result = fx(budgetController::lastResult);
            assertNotNull(result);
            assertTrue(result.feasible(), () -> "expected a feasible plan, got " + result.infeasibleReasons());
            assertTrue(result.primary().breakdown().withinBudget());
            assertTrue(result.primary().breakdown().total().signum() > 0);
            assertFalse(result.primary().selected().isEmpty());
            assertTrue(fx(() -> budgetView.results().getChildren().size()) >= 4,
                "the results panel did not render");
            scrollTo(1.0);
            screenshot("budget-optimizer.png");
            scrollTo(0.42);
            screenshot("budget-optimizer-summary.png");
            scrollTo(0.0);
            screenshot("budget-optimizer-form.png");

            // Explore Alternatives: the panel is present, every option is a real button, and
            // applying one re-runs the optimiser off the UI thread and reports what moved
            assertNotNull(fx(() -> budgetView.lookup("#budget-explore")), "the what-if panel is missing");
            long whatIfCount = fx(() -> budgetView.lookupAll("#budget-explore .button").size());
            assertTrue(whatIfCount >= 4, "expected several what-if options, found " + whatIfCount);
            BigDecimal before = fx(() -> budgetController.lastResult().primary().breakdown().total());
            fx(() -> {
                budgetView.lookupAll("#budget-explore .button").stream()
                    .map(node -> (Button) node)
                    .filter(button -> button.getText().startsWith("Switch to Lowest Cost"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no mode-switch what-if option"))
                    .fire();
                return null;
            });
            until(() -> budgetController.lastResult().primary().mode() == RecommendationMode.LOWEST_COST,
                "the what-if option did not re-optimise");
            BigDecimal after = fx(() -> budgetController.lastResult().primary().breakdown().total());
            assertTrue(after.compareTo(before) <= 0, "Lowest Cost must not cost more than Best Value");
            assertTrue(fx(() -> budgetView.results().getChildren().size()) >= 5,
                "the change card did not render above the summary");
            scrollTo(0.45);
            screenshot("budget-optimizer-whatif.png");

            // optional charges: a delivery fee and a discount must show up on the individual cards
            fx(() -> {
                ((TextField) budgetView.lookup("#budget-delivery-walmart")).setText("5.00");
                ((TextField) budgetView.lookup("#budget-discount-percent")).setText("10");
                ((TextField) budgetView.lookup("#budget-discount-minimum")).setText("50");
                ((Button) budgetView.lookup("#budget-optimize")).fire();
                return null;
            });
            until(() -> budgetController.lastResult() != null
                && budgetController.lastRequest() != null
                && budgetController.lastRequest().deliveryFeeByStore()
                    .getOrDefault("Walmart", BigDecimal.ZERO).signum() > 0, "charges were not applied");
            // NB: the condition already runs on the FX thread — calling fx() inside it would deadlock
            until(() -> budgetView.lookupAll(".line-charges").size() > 0,
                "no product card showed its share of the delivery fee / discount");
            assertTrue(fx(() -> budgetController.lastResult().primary().breakdown().notes().stream()
                    .anyMatch(note -> note.toLowerCase().contains("delivery"))),
                "the assumptions the shopper typed in must be disclosed in the notes");
            scrollTo(1.0);
            screenshot("budget-optimizer-charges.png");
            fx(() -> {
                ((TextField) budgetView.lookup("#budget-delivery-walmart")).setText("0.00");
                ((TextField) budgetView.lookup("#budget-discount-percent")).setText("0.00");
                ((TextField) budgetView.lookup("#budget-discount-minimum")).setText("0.00");
                return null;
            });

            // save + view: the recommendation is written to the JSON store and can be re-opened
            fx(() -> {
                ((Button) budgetView.lookup("#budget-save")).fire();
                return null;
            });
            until(() -> {
                try {
                    return !new JsonBudgetHistoryDao(historyFile).list().isEmpty();
                } catch (Exception e) {
                    return false;
                }
            }, "the recommendation was not persisted");
            until(() -> budgetView.lookupAll("#budget-saved .button").size() > 0,
                "the saved list did not offer a way to view the stored plan");
            SavedBudgetRun stored = new JsonBudgetHistoryDao(historyFile).list().get(0);
            assertEquals(0, stored.total().compareTo(
                    fx(() -> budgetController.lastResult().primary().breakdown().total())),
                "the stored total must equal the recommendation that was on screen");
            assertFalse(stored.lines().isEmpty());
            int savedNodes = fx(() -> budgetView.buildSavedRunDocument(stored).getChildren().size());
            assertTrue(savedNodes > stored.lines().size(),
                "the saved-plan viewer looks empty: " + savedNodes + " nodes for "
                    + stored.lines().size() + " lines");

            // the printable document is built from the same recommendation (no print service needed here)
            int printableNodes = fx(() -> budgetView.buildPrintableDocument().getChildren().size());
            assertTrue(printableNodes > 8, "the printable plan looks empty: " + printableNodes);
            scrollTo(0.0);

            // INR entry: the shopper types rupees and their own rate; the optimiser still receives the
            // catalogue currency, and the screen shows the amount in rupees
            fx(() -> {
                ((javafx.scene.control.ComboBox<String>) budgetView.lookup("#budget-currency"))
                    .getSelectionModel().select(1);
                ((TextField) budgetView.lookup("#budget-inr-rate")).setText("83.50");
                ((TextField) budgetView.lookup("#budget-amount")).setText("200000");
                ((Button) budgetView.lookup("#budget-optimize")).fire();
                return null;
            });
            // 200000 / 83.50 = 2395.2096 -> 2395.21 in catalogue currency.
            // NB: wait for the rendered result, not just the request — the worker finishes after it.
            until(() -> budgetController.lastRequest() != null
                && budgetController.lastRequest().budget().compareTo(new BigDecimal("2395.20")) > 0
                && budgetController.lastRequest().budget().compareTo(new BigDecimal("2395.22")) < 0
                && budgetView.lookup(".kpi-value") != null
                && ((Label) budgetView.lookup(".kpi-value")).getText().startsWith("INR 2,00,000.00"),
                "the INR budget was not converted and rendered in rupees");
            assertEquals("USD", fx(() -> budgetController.lastRequest().currency()),
                "the optimiser must keep working in the catalogue currency");
            assertTrue(fx(() -> budgetController.lastResult().feasible()));
            String budgetKpi = fx(() -> ((Label) budgetView.lookup(".kpi-value")).getText());
            assertTrue(budgetKpi.startsWith("INR 2,00,000.00"),
                "the budget must read exactly as typed, in rupees: " + budgetKpi);
            assertNotNull(fx(() -> budgetView.lookup("#budget-quick-200000")),
                "the quick-fill chips did not switch to rupee-sized amounts");
            until(() -> ((Label) budgetView.lookup("#budget-conversion-note")).getText().contains("83.50"),
                "the conversion assumption is not disclosed");
            assertTrue(fx(() -> ((Label) budgetView.lookup("#budget-conversion-note")).getText()
                    .toLowerCase().contains("does not supply exchange rates")),
                "the app must state that it does not supply rates");
            BigDecimal inrBudgetShown = fx(() -> budgetView.converter().toDisplay(
                budgetController.lastRequest().budget()));
            assertTrue(fx(() -> budgetController.lastResult().primary().breakdown().total())
                .compareTo(BigDecimal.ZERO) > 0);
            assertTrue(fx(() -> budgetController.lastResult().primary().breakdown().total()
                    .compareTo(budgetController.lastRequest().budget()) <= 0),
                "the basket must still fit inside the converted budget");
            assertTrue(inrBudgetShown.compareTo(new BigDecimal("199900")) > 0);
            scrollTo(0.0);
            screenshot("budget-optimizer-inr.png");
            scrollTo(0.45);
            screenshot("budget-optimizer-inr-results.png");
            scrollTo(0.0);
            // back to the catalogue currency for the remaining checks
            fx(() -> {
                ((javafx.scene.control.ComboBox<String>) budgetView.lookup("#budget-currency"))
                    .getSelectionModel().select(0);
                ((TextField) budgetView.lookup("#budget-amount")).setText("2500");
                return null;
            });

            // a budget nothing can satisfy: the UI must explain, not crash
            fx(() -> {
                ((TextField) budgetView.lookup("#budget-amount")).setText("40");
                ((Button) budgetView.lookup("#budget-optimize")).fire();
                return null;
            });
            until(() -> budgetController.lastResult() != null && !budgetController.lastResult().feasible(),
                "the infeasible request was not reported");
            OptimizationResult infeasible = fx(budgetController::lastResult);
            assertFalse(infeasible.suggestions().isEmpty());
            assertTrue(fx(() -> budgetView.results().getChildren().size()) >= 1);
            scrollTo(1.0);
            screenshot("budget-optimizer-infeasible.png");
            scrollTo(0.0);

            // invalid input is rejected with a message and no result screen
            fx(() -> {
                ((TextField) budgetView.lookup("#budget-amount")).setText("-10");
                ((Button) budgetView.lookup("#budget-optimize")).fire();
                return null;
            });
            Thread.sleep(400);
            assertTrue(fx(() -> ((Label) budgetView.lookup(".kpi-warn")).getText())
                .toLowerCase().contains("greater than zero"));
        } finally {
            fx(() -> {
                if (budgetController != null) {
                    budgetController.close();
                }
                if (catalogController != null) {
                    catalogController.close();
                }
                for (Window window : List.copyOf(Window.getWindows())) {
                    window.hide();
                }
                return null;
            });
            Platform.exit();
        }
    }

    private void scrollTo(double position) throws Exception {
        fx(() -> {
            ((javafx.scene.control.ScrollPane) budgetView.getCenter()).setVvalue(position);
            return null;
        });
        Thread.sleep(300);
    }

    private void screenshot(String name) throws Exception {
        if (!Boolean.getBoolean("tulita.screenshots")) {
            return;
        }
        fx(() -> {
            catalogView.applyCss();
            catalogView.layout();
            return null;
        });
        Thread.sleep(400);
        Rectangle bounds = fx(() -> new Rectangle((int) stage.getX(), (int) stage.getY(),
            (int) stage.getWidth(), (int) stage.getHeight()));
        ImageIO.write(new Robot().createScreenCapture(bounds), "png", Path.of("docs", name).toFile());
    }
}
