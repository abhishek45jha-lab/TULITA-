package tulita.photocatalog.view;
import tulita.photocatalog.camera.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
public final class CameraDialog{
 @FunctionalInterface public interface Provider{CameraSource open()throws Exception;}
 public static void show(Window owner,Provider provider,Consumer<BufferedImage> onPhoto){
  Dialog<Void> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Tulita · Take a photo");dialog.getDialogPane().setId("photo-camera-dialog");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
  Label status=new Label("Opening the default system webcam…");status.setWrapText(true);status.setMaxWidth(620);status.setId("camera-status");ImageView preview=new ImageView();preview.setFitWidth(620);preview.setFitHeight(420);preview.setPreserveRatio(true);Button snap=new Button("Capture & find catalog matches");snap.setId("camera-capture");snap.setDisable(true);
  VBox content=new VBox(12,status,preview,snap,new Label("Photos stay local. Confirm the suggested product and exact variant."));content.setPadding(new javafx.geometry.Insets(18));content.setPrefWidth(650);dialog.getDialogPane().setContent(content);
  AtomicBoolean closed=new AtomicBoolean();AtomicReference<BufferedImage> latest=new AtomicReference<>();AtomicReference<CameraSource> source=new AtomicReference<>();ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"tulita-camera");t.setDaemon(true);return t;});
  snap.setOnAction(e->{BufferedImage image=latest.get();if(image!=null){dialog.close();onPhoto.accept(image);}});
  dialog.setOnHidden(e->{closed.set(true);worker.shutdownNow();CameraSource cam=source.getAndSet(null);if(cam!=null){Thread closer=new Thread(cam::close,"tulita-camera-close");closer.setDaemon(true);closer.start();}});
  worker.submit(()->{try{var cam=provider.open();if(closed.get()){cam.close();return;}source.set(cam);if(closed.get()){if(source.getAndSet(null)!=null)cam.close();return;}worker.scheduleWithFixedDelay(()->{if(closed.get())return;try{BufferedImage frame=cam.frame();if(frame!=null){latest.set(frame);Platform.runLater(()->{if(!closed.get()){preview.setImage(SwingFXUtils.toFXImage(frame,null));snap.setDisable(false);status.setText("Camera: "+cam.name()+" · Center one product, use good light, then capture.");}});}}catch(Throwable ex){Platform.runLater(()->{if(!closed.get()){snap.setDisable(true);status.setText("Camera stopped. Close this window and use Upload photo, or reconnect your webcam.");}});}},0,250,TimeUnit.MILLISECONDS);}catch(Throwable ex){Platform.runLater(()->{if(!closed.get())status.setText("Camera unavailable. Check OS camera permissions, drivers and whether another app is using it. Close this window and use Upload photo instead. No camera image was captured.");});}});
  dialog.show();
 }
}
