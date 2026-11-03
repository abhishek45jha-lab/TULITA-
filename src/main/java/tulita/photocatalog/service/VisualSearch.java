package tulita.photocatalog.service;
import ai.onnxruntime.*;
import tulita.photocatalog.model.PhotoProduct;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import java.util.function.Consumer;
/** CLIP image-to-image retrieval on CPU. Similarity is NOT identity confidence. */
public final class VisualSearch implements AutoCloseable{
 public record Hit(PhotoProduct product,double similarity){}
 private record Reference(PhotoProduct product,float[] vector){}
 private final OrtEnvironment env=OrtEnvironment.getEnvironment();private final OrtSession session;private final List<Reference> references=new ArrayList<>();
 public VisualSearch(Path model)throws Exception{ModelFiles.verify(model);var options=new OrtSession.SessionOptions();options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);try{session=env.createSession(model.toString(),options);}finally{options.close();}}
 public void index(List<PhotoProduct> data,Path root,Consumer<String> status)throws Exception{
  List<Reference> next=new ArrayList<>();int total=data.stream().mapToInt(p->p.photos().size()).sum(),done=0;
  for(var p:data)for(var photo:p.photos()){if(Thread.currentThread().isInterrupted())throw new InterruptedException();var image=read(CatalogValidator.safe(root,photo.path()));next.add(new Reference(p,embed(image)));status.accept("Indexing reference photos "+(++done)+" / "+total+"…");}
  references.clear();references.addAll(next);
 }
 public static BufferedImage read(Path path)throws Exception{long size=java.nio.file.Files.size(path);if(size>25L*1024*1024)throw new IllegalArgumentException("Choose a JPG/PNG photo under 25 MB.");try(var input=ImageIO.createImageInputStream(path.toFile())){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Unsupported image. Use JPG or PNG.");var reader=readers.next();try{reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>40_000_000L)throw new IllegalArgumentException("Image exceeds 40 megapixels. Resize it first.");var image=reader.read(0);if(image==null)throw new IllegalArgumentException("Cannot decode image.");return image;}finally{reader.dispose();}}}
 public float[] embed(BufferedImage image)throws Exception{
  float[] pixels=preprocess(image);try(var tensor=OnnxTensor.createTensor(env,FloatBuffer.wrap(pixels),new long[]{1,3,224,224});var result=session.run(Map.of("pixel_values",tensor))){var output=result.get("image_embeds").orElseThrow(()->new IllegalStateException("Model has no image embedding output."));float[] vector=((float[][])output.getValue())[0];return normalize(vector);}
 }
 public List<Hit> search(BufferedImage image,int limit)throws Exception{if(references.isEmpty())throw new IllegalStateException("Reference photo index is not ready.");float[] query=embed(image);Map<String,Hit> best=new HashMap<>();for(var ref:references){double score=cosine(query,ref.vector());Hit before=best.get(ref.product().id());if(before==null||score>before.similarity())best.put(ref.product().id(),new Hit(ref.product(),score));}return best.values().stream().sorted(Comparator.comparingDouble(Hit::similarity).reversed().thenComparing(h->h.product().id())).limit(Math.max(1,limit)).toList();}
 public static float[] preprocess(BufferedImage source){if(source==null||source.getWidth()<1||source.getHeight()<1)throw new IllegalArgumentException("Empty image.");int w=source.getWidth(),h=source.getHeight();double scale=224.0/Math.max(w,h);int rw=Math.max(1,(int)Math.round(w*scale)),rh=Math.max(1,(int)Math.round(h*scale));BufferedImage rgb=new BufferedImage(224,224,BufferedImage.TYPE_INT_RGB);Graphics2D g=rgb.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,224,224);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(source,(224-rw)/2,(224-rh)/2,rw,rh,null);g.dispose();float[] mean={.48145466f,.4578275f,.40821073f},std={.26862954f,.26130258f,.27577711f},out=new float[3*224*224];for(int y=0;y<224;y++)for(int x=0;x<224;x++){int c=rgb.getRGB(x,y);for(int channel=0;channel<3;channel++){float v=((c>>(16-channel*8))&255)/255f;out[channel*224*224+y*224+x]=(v-mean[channel])/std[channel];}}return out;}
 private static float[] normalize(float[] v){double norm=0;for(float n:v)norm+=n*n;norm=Math.sqrt(norm);if(norm==0)throw new IllegalArgumentException("Zero image embedding.");float[] copy=v.clone();for(int i=0;i<copy.length;i++)copy[i]/=(float)norm;return copy;}
 public static double cosine(float[] a,float[] b){if(a.length!=b.length)throw new IllegalArgumentException("Embedding dimensions differ.");double sum=0;for(int i=0;i<a.length;i++)sum+=a[i]*b[i];return sum;}
 public void close()throws OrtException{session.close();}
}
