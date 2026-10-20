package tulita.dataset;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import tulita.dataset.view.DatasetView;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.nio.file.*;
import java.awt.Robot;
import java.awt.Rectangle;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class DatasetUiTest {
    private Stage stage;
    private DatasetView view;
    private DatasetController controller;
    private <T>T fx(Callable<T> work)throws Exception{FutureTask<T> task=new FutureTask<>(work);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    private void until(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(!fx(condition)){if(System.nanoTime()>end)fail("Dataset UI did not reach expected state");Thread.sleep(60);}}
    @Test void fullDatasetSearchPaginationDetailsWatchlist() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.datasetUiTests"));
        CompletableFuture<Void> ready=new CompletableFuture<>(); Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});ready.get(10,TimeUnit.SECONDS);
        try {
            fx(()->{view=new DatasetView();stage=new Stage();Scene scene=new Scene(view,1280,960);scene.getStylesheets().add(getClass().getResource("/styles/tulita.css").toExternalForm());stage.setScene(scene);stage.setTitle("Tulita · Historical Flipkart catalog");stage.show();controller=new DatasetController(view,stage,u->{});controller.load();return null;});
            until(()->((Label)view.lookup("#dataset-count")).getText().contains("20,000"));
            fx(()->{assertEquals(12,((FlowPane)view.lookup("#dataset-grid")).getChildren().size());assertFalse(view.lookup("#dataset-next").isDisabled());((Button)view.lookup("#dataset-next")).fire();return null;});
            until(()->((Label)view.lookup("#dataset-page")).getText().contains("2 /"));
            fx(()->{TextField f=(TextField)view.lookup("#dataset-search");f.setText("samsung");f.fireEvent(new ActionEvent());return null;});
            until(()->((Label)view.lookup("#dataset-page")).getText().contains("1 /")&&!((Label)view.lookup("#dataset-progress")).getText().contains("Searching"));
            screenshot("dataset-dashboard.png");
            String id=fx(()->((FlowPane)view.lookup("#dataset-grid")).getChildren().get(0).getId().substring("record-".length()));
            fx(()->{((Button)view.lookup("#detail-"+id)).fire();return null;});
            fx(()->{assertTrue(Window.getWindows().stream().filter(Window::isShowing).anyMatch(w->w.getScene().lookup("#dataset-detail")!=null));return null;});
            screenshot("dataset-record.png");
            fx(()->{for(Window w:java.util.List.copyOf(Window.getWindows()))if(w!=stage)w.hide();((Button)view.lookup("#save-"+id)).fire();return null;});
            until(()->!((Label)view.lookup("#dataset-progress")).getText().contains("Searching"));
            fx(()->{((Button)view.lookup("#dataset-watchlist")).fire();return null;});
            until(()->((Label)view.lookup("#dataset-count")).getText().contains("watchlist")&&((Label)view.lookup("#dataset-count")).getText().endsWith("1 results"));
            fx(()->{((Button)view.lookup("#save-"+id)).fire();return null;});
            until(()->((Label)view.lookup("#dataset-count")).getText().endsWith("0 results"));
            fx(()->{((Button)view.lookup("#dataset-discover")).fire();((TextField)view.lookup("#dataset-search")).clear();((ComboBox<String>)view.lookup("#dataset-category")).getSelectionModel().select("Footwear");((ComboBox<?>)view.lookup("#dataset-sort")).getSelectionModel().select(1);return null;});
            until(()->((Label)view.lookup("#dataset-count")).getText().contains("1,227 results"));
            fx(()->{((TextField)view.lookup("#dataset-search")).setText("no-such-record-zz1234");return null;});
            until(()->((Label)view.lookup("#dataset-count")).getText().endsWith("0 results"));
        }finally{fx(()->{if(controller!=null)controller.close();for(Window w:java.util.List.copyOf(Window.getWindows()))w.hide();return null;});Platform.exit();}
    }
    private void screenshot(String name)throws Exception{
        if(!Boolean.getBoolean("tulita.screenshots"))return;
        fx(()->{view.applyCss();view.layout();return null;});Thread.sleep(300);Files.createDirectories(Path.of("docs"));
        Rectangle bounds=fx(()->new Rectangle((int)stage.getX(),(int)stage.getY(),(int)stage.getWidth(),(int)stage.getHeight()));
        ImageIO.write(new Robot().createScreenCapture(bounds),"png",Path.of("docs",name).toFile());
    }
}
