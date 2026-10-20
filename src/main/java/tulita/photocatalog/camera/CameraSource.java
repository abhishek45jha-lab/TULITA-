package tulita.photocatalog.camera;
import java.awt.image.BufferedImage;
public interface CameraSource extends AutoCloseable{BufferedImage frame();String name();void close();}
