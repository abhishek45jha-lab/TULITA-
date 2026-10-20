package tulita.dataset;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import tulita.dataset.view.DatasetView;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.awt.Robot;
import java.awt.Rectangle;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class MultiStoreUiTest {
    Stage stage;DatasetView view;DatasetController controller;
    <T>T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    void until(Callable<Boolean> test)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);while(!fx(test)){if(System.nanoTime()>end)fail("Multi-store UI not ready");Thread.sleep(70);}}
    @Test void strictDefaultAndHonestMissingStores()throws Exception{
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.multiUiTests"));CompletableFuture<Void> ready=new CompletableFuture<>();Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});ready.get(10,TimeUnit.SECONDS);
        try{
            fx(()->{view=new DatasetView(true);stage=new Stage();Scene scene=new Scene(view,1320,990);scene.getStylesheets().add(getClass().getResource("/styles/tulita.css").toExternalForm());stage.setScene(scene);stage.show();controller=new DatasetController(view,stage,u->{},true);controller.load();return null;});
            until(()->((Label)view.lookup("#dataset-count")).getText().contains("0 results")&&!((Label)view.lookup("#dataset-progress")).getText().contains("Searching"));
            fx(()->{assertTrue(((CheckBox)view.lookup("#same-product-three-only")).isSelected());assertNull(view.lookup("#compare-selected"));return null;});screenshot("same-product-empty.png");
            fx(()->{((CheckBox)view.lookup("#same-product-three-only")).fire();((TextField)view.lookup("#dataset-search")).setText("NL1805WL01");return null;});
            until(()->!((Label)view.lookup("#dataset-progress")).getText().contains("Searching")&&view.lookup("#dataset-grid")!=null&&((FlowPane)view.lookup("#dataset-grid")).getChildren().stream().anyMatch(n->n.getId().contains("Myntra")));
            screenshot("same-product-browse.png");
            fx(()->{var grid=(FlowPane)view.lookup("#dataset-grid");String id=grid.getChildren().stream().filter(n->n.getId().contains("Myntra")).findFirst().orElseThrow().getId().substring(7);assertNull(view.lookup("#pick-"+id));((Button)view.lookup("#detail-"+id)).fire();return null;});
            until(()->Window.getWindows().stream().anyMatch(w->w!=stage&&w.isShowing()&&w.getScene().lookup("#same-product-table")!=null));
            fx(()->{Window w=Window.getWindows().stream().filter(x->x!=stage&&x.isShowing()).findFirst().orElseThrow();TableView<?> table=(TableView<?>)w.getScene().lookup("#same-product-table");assertEquals(3,table.getItems().size());assertEquals(2,table.getItems().stream().filter(r->r.toString().contains("listing=null")).count());assertTrue(((Label)w.getScene().lookup("#same-product-identity")).getText().contains("UNMAPPED"));return null;});screenshot("same-product-missing.png");
            // Positive rendering uses fictional fixtures confined to tests, never distributed as genuine mappings.
            fx(()->{for(Window w:List.copyOf(Window.getWindows()))if(w!=stage)w.hide();var rows=tulita.matching.service.SameProductService.STORES.stream().map(tulita.matching.ReviewFixtures::row).toList();var strict=new tulita.matching.service.SameProductService(tulita.matching.ReviewFixtures.snapshot(rows),rows.stream().map(tulita.matching.ReviewFixtures::link).toList());tulita.matching.view.SameProductComparisonView.show(stage,rows.get(0),strict.groupFor(rows.get(0).recordId()),p->{});return null;});
            fx(()->{Window w=Window.getWindows().stream().filter(x->x!=stage&&x.isShowing()).findFirst().orElseThrow();TableView<?> table=(TableView<?>)w.getScene().lookup("#same-product-table");assertEquals(3,table.getItems().size());assertTrue(table.getItems().stream().noneMatch(r->r.toString().contains("listing=null")));assertTrue(((Label)w.getScene().lookup("#same-product-identity")).getText().contains("REVIEWED IDENTITY LINKS"));return null;});
        }finally{fx(()->{if(controller!=null)controller.close();for(Window w:List.copyOf(Window.getWindows()))w.hide();return null;});Platform.exit();}
    }
    void screenshot(String name)throws Exception{if(!Boolean.getBoolean("tulita.screenshots"))return;fx(()->{view.applyCss();view.layout();return null;});Thread.sleep(350);Rectangle r=fx(()->new Rectangle((int)stage.getX(),(int)stage.getY(),(int)stage.getWidth(),(int)stage.getHeight()));ImageIO.write(new Robot().createScreenCapture(r),"png",Path.of("docs",name).toFile());}
}
