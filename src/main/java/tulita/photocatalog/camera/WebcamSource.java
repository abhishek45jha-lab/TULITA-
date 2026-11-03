package tulita.photocatalog.camera;
import com.github.sarxos.webcam.Webcam;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
/** Physical default system webcam. No browser, network upload or simulated camera frames. */
public final class WebcamSource implements CameraSource{
 private final Webcam camera;
 private WebcamSource(Webcam camera){this.camera=camera;}
 public static CameraSource open()throws Exception{Webcam cam=Webcam.getDefault(8,TimeUnit.SECONDS);if(cam==null)throw new IllegalStateException("No webcam found. Connect/enable one or use Upload photo.");if(!cam.open())throw new IllegalStateException("Webcam could not open. Close other camera applications or use Upload photo.");return new WebcamSource(cam);}
 public BufferedImage frame(){return camera.getImage();}public String name(){return camera.getName();}public void close(){camera.close();}
}
