package tulita.photocatalog.service;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.function.Consumer;
/** Optional one-time model download. No image or query is uploaded. */
public final class ModelFiles{
 public static final String SHA="583fd1110a514667812fee7d684952aaf82a99b959760c8d7dca7e0ab9839299";
 public static final String URL="https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/d15189d7028b43f1d3e65039190477f6af591c2a/onnx/vision_model_quantized.onnx";
 public static Path path(){return Path.of(System.getenv().getOrDefault("TULITA_VISION_MODEL_PATH",Path.of(System.getProperty("user.home"),".tulita","clip-vision-int8.onnx").toString()));}
 public static boolean present(){return Files.isRegularFile(path());}
 public static void verify(Path p)throws Exception{if(!CatalogValidator.sha(Files.readAllBytes(p)).equals(SHA))throw new IllegalArgumentException("Offline model hash is wrong. Re-download it; no alternative model/fallback is used.");}
 public static Path install(Consumer<String> status)throws Exception{
  Path path=path();if(Files.exists(path)){verify(path);return path;}
  Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,"clip-download-",".part");
  try{status.accept("Downloading 89 MB offline image model. Your photos are not uploaded…");var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();var request=HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofMinutes(8)).header("User-Agent","Tulita-Academic-Desktop/1.0").GET().build();var response=client.send(request,HttpResponse.BodyHandlers.ofFile(temp));if(response.statusCode()!=200)throw new java.io.IOException("Model server returned "+response.statusCode());verify(temp);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);return path;}finally{Files.deleteIfExists(temp);}
 }
}
