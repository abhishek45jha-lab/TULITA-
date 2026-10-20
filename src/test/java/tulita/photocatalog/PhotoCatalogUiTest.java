package tulita.photocatalog;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import tulita.photocatalog.view.*;
import tulita.photocatalog.service.*;
import tulita.photocatalog.dao.*;
import tulita.photocatalog.camera.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.awt.Robot;
import java.awt.Rectangle;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class PhotoCatalogUiTest{
 Stage stage;PhotoCatalogView view;PhotoCatalogController controller;
 <T>T fx(Callable<T> call)throws Exception{FutureTask<T> task=new FutureTask<>(call);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
 void until(Callable<Boolean> condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(75);while(!fx(condition)){if(System.nanoTime()>deadline)fail("Photo catalog UI did not reach expected state");Thread.sleep(80);}}
 Window dialog(String id){return Window.getWindows().stream().filter(w->w!=stage&&w.isShowing()&&w.getScene().lookup(id)!=null).findFirst().orElseThrow();}
 @Test void actualCatalogPhotosComparisonVisionAndCameraControls()throws Exception{
  Assumptions.assumeTrue(Boolean.getBoolean("tulita.photoUiTests"));CompletableFuture<Void> ready=new CompletableFuture<>();Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});ready.get(10,TimeUnit.SECONDS);
  Path root=JsonPhotoCatalogDao.defaultRoot();BufferedImage captured=VisualSearch.read(root.resolve("images/reynolds-amazon-0.jpg"));
  try{
   fx(()->{view=new PhotoCatalogView();stage=new Stage();Scene scene=new Scene(view,1320,1000);scene.getStylesheets().add(getClass().getResource("/styles/tulita.css").toExternalForm());stage.setScene(scene);stage.show();controller=new PhotoCatalogController(view,stage,url->{},()->new CameraSource(){public BufferedImage frame(){return captured;}public String name(){return "TEST ONLY injected camera";}public void close(){}});controller.load();return null;});
   until(()->((Label)view.lookup("#catalog-count")).getText().contains("79 results"));screenshot("photo-catalog-dashboard.png");
   fx(()->{((TextField)view.lookup("#catalog-search")).setText("58149");((Button)view.lookup("#catalog-compare-hamilton")).fire();return null;});
   fx(()->{var table=(TableView<?>)dialog("#catalog-comparison-dialog").getScene().lookup("#catalog-comparison-table");assertEquals(4,table.getItems().size());assertEquals(1,table.getItems().stream().filter(r->r.toString().contains("offer=null")).count());assertTrue(table.getItems().toString().contains("52.99"));return null;});screenshot("photo-catalog-comparison.png");
   fx(()->{dialog("#catalog-comparison-dialog").hide();((TextField)view.lookup("#catalog-search")).setText("dartboard");((Button)view.lookup("#catalog-compare-franklin-dartboard")).fire();return null;});
   fx(()->{var table=(TableView<?>)dialog("#catalog-comparison-dialog").getScene().lookup("#catalog-comparison-table");assertEquals(4,table.getItems().size());assertTrue(table.getItems().toString().contains("34.99"));assertTrue(table.getItems().toString().contains("23.99"));return null;});screenshot("photo-catalog-price-spread.png");
   fx(()->{dialog("#catalog-comparison-dialog").hide();((TextField)view.lookup("#catalog-search")).clear();((CheckBox)view.lookup("#catalog-three")).fire();assertTrue(((Label)view.lookup("#catalog-count")).getText().contains("3 results"));controller.searchPhoto(root.resolve("images/reynolds-amazon-0.jpg"));return null;});
   until(()->((Label)view.lookup("#catalog-count")).getText().contains("5 results")&&((Label)view.lookup("#catalog-status")).getText().contains("Top visual suggestions"));
   fx(()->{assertFalse(((CheckBox)view.lookup("#catalog-three")).isSelected());assertTrue(((TextField)view.lookup("#catalog-search")).getText().isEmpty());assertNotNull(view.lookup("#catalog-compare-reynolds"));return null;});screenshot("photo-catalog-visual-search.png");
   fx(()->{((Button)view.lookup("#catalog-camera")).fire();return null;});until(()->{try{return !((Button)dialog("#photo-camera-dialog").getScene().lookup("#camera-capture")).isDisabled();}catch(NoSuchElementException e){return false;}});
   fx(()->{((Button)dialog("#photo-camera-dialog").getScene().lookup("#camera-capture")).fire();return null;});until(()->!((Button)view.lookup("#catalog-camera")).isDisabled());
   // Failure path also exercised using an explicit test-only provider.
   fx(()->{CameraDialog.show(stage,()->{throw new IllegalStateException("TEST ONLY no device");},img->fail("No capture allowed without a device"));return null;});until(()->{try{return ((Label)dialog("#photo-camera-dialog").getScene().lookup("#camera-status")).getText().contains("Camera unavailable");}catch(NoSuchElementException e){return false;}});fx(()->{assertTrue(((Button)dialog("#photo-camera-dialog").getScene().lookup("#camera-capture")).isDisabled());return null;});
  }finally{fx(()->{if(controller!=null)controller.close();for(Window w:List.copyOf(Window.getWindows()))w.hide();return null;});Platform.exit();}
 }
 void screenshot(String name)throws Exception{if(!Boolean.getBoolean("tulita.screenshots"))return;fx(()->{view.applyCss();view.layout();return null;});Thread.sleep(200);Rectangle bounds=fx(()->new Rectangle((int)stage.getX(),(int)stage.getY(),(int)stage.getWidth(),(int)stage.getHeight()));ImageIO.write(new Robot().createScreenCapture(bounds),"png",Path.of("docs",name).toFile());}
}
