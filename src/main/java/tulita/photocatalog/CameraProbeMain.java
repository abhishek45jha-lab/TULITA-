package tulita.photocatalog;
/** Optional local diagnostic. Does not save/upload the camera frame. */
public final class CameraProbeMain{
 public static void main(String[]args){try(var camera=tulita.photocatalog.camera.WebcamSource.open()){var image=camera.frame();System.out.println("Camera opened: "+camera.name()+"; frame available: "+(image!=null)+". No photo was saved or uploaded.");}catch(Throwable e){System.out.println("Physical camera unavailable in this environment. Check OS permissions, device drivers and other camera apps. Upload photo remains available. No camera capture was claimed.");}}
}
