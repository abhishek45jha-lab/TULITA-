package tulita;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import tulita.view.*;
import tulita.controller.DashboardController;
import java.util.concurrent.*;
import java.nio.file.*;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Runs real JavaFX controls. Opt-in so ordinary headless mvn test stays portable. */
class UiSmokeTest {
    private Stage stage;
    private DashboardView view;
    private DashboardController controller;
    private <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task); return task.get(15,TimeUnit.SECONDS);
    }
    @Test void realDesktopSearchCompareWatchlistAndFilters() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.uiTests"));
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(()-> { Platform.setImplicitExit(false); started.complete(null); }); started.get(10,TimeUnit.SECONDS);
        try {
            fx(()-> {
                view=new DashboardView(); stage=new Stage(); Scene scene=new Scene(view,1280,850);
                scene.getStylesheets().add(getClass().getResource("/styles/tulita.css").toExternalForm());
                stage.setScene(scene); stage.setTitle("Tulita — Smart E-Commerce Price Comparison"); stage.show();
                controller=new DashboardController(view,stage,url -> { }); controller.load(); return null;
            });
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!fx(()->((Label)view.lookup("#mode-label")).getText().contains("SAMPLE DATA"))) {
                if(System.nanoTime()>deadline) fail("Demo catalog did not load"); Thread.sleep(60);
            }
            fx(()-> { assertEquals(20,((FlowPane)view.lookup("#product-grid")).getChildren().size()); return null; });
            screenshot("dashboard.png");
            fx(()-> { TextField field=(TextField)view.lookup("#search"); field.setText("pEgAsUs 40"); field.fireEvent(new ActionEvent());
                assertEquals(1,((FlowPane)view.lookup("#product-grid")).getChildren().size());
                ((Button)view.lookup("#compare-7")).fire(); return null; });
            fx(()-> {
                Window comparison=Window.getWindows().stream().filter(w->w!=stage&&w.isShowing()).findFirst().orElseThrow();
                var table=(TableView<?>)comparison.getScene().lookup("#comparison-table"); assertEquals(3,table.getItems().size());
                assertTrue(((Label)comparison.getScene().lookup("#best-banner")).getText().contains("7,495")); return null;
            });
            screenshot("comparison.png");
            fx(()-> {
                for(Window w:java.util.List.copyOf(Window.getWindows())) if(w!=stage) w.hide();
                ((Button)view.lookup("#watch-7")).fire(); ((Button)view.lookup("#watchlist")).fire();
                assertEquals(1,((FlowPane)view.lookup("#product-grid")).getChildren().size());
                ((Button)view.lookup("#watch-7")).fire(); assertTrue(((Label)view.lookup("#result-count")).getText().endsWith("0"));
                ((Button)view.lookup("#discover")).fire(); ((TextField)view.lookup("#search")).clear();
                ((Button)view.lookup("#category-footwear")).fire(); assertEquals(5,((FlowPane)view.lookup("#product-grid")).getChildren().size());
                ((ComboBox<?>)view.lookup("#sort")).getSelectionModel().select(1);
                ((TextField)view.lookup("#search")).setText("no-such-product");
                assertTrue(((Label)view.lookup("#result-count")).getText().endsWith("0"));
                stage.setWidth(1080); stage.setHeight(720);
                view.showError("MySQL setup required. Check schema.sql and credentials.");
                assertTrue(view.lookup("#search").isDisabled());
                assertFalse(view.lookup("#retry").isDisabled());
                ((Button)view.lookup("#retry")).fire();
                assertTrue(((Label)view.lookup("#result-count")).getText().contains("Loading")); return null;
            });
        } finally {
            fx(()-> { if(controller!=null) controller.close(); for(Window w:java.util.List.copyOf(Window.getWindows())) w.hide(); return null; });
            Platform.exit();
        }
    }
    private void screenshot(String name) throws Exception {
        if (!Boolean.getBoolean("tulita.screenshots")) return;
        fx(()-> { view.applyCss(); view.layout(); return null; });
        Thread.sleep(350);
        Path folder=Path.of("docs"); Files.createDirectories(folder);
        Rectangle bounds=fx(()->new Rectangle((int)stage.getX(),(int)stage.getY(),(int)stage.getWidth(),(int)stage.getHeight()));
        ImageIO.write(new Robot().createScreenCapture(bounds),"png",folder.resolve(name).toFile());
    }
}
