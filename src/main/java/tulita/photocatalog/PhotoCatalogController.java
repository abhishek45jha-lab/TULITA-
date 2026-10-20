package tulita.photocatalog;
import javafx.application.Platform;
import javafx.stage.*;
import javafx.scene.control.*;
import javafx.embed.swing.SwingFXUtils;
import tulita.photocatalog.view.*;
import tulita.photocatalog.service.*;
import tulita.photocatalog.dao.*;
import tulita.photocatalog.camera.*;
import tulita.photocatalog.model.PhotoProduct;
import tulita.database.*;
import tulita.dataset.DatasetConfig;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
public final class PhotoCatalogController implements AutoCloseable{
 private final PhotoCatalogView view;private final Window owner;private final Consumer<String> opener;private final CameraDialog.Provider cameraProvider;private final Path root=JsonPhotoCatalogDao.defaultRoot();private final Set<String> saved=new HashSet<>();private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"tulita-photo-worker");t.setDaemon(true);return t;});private CatalogService service;private VisualSearch vision;private List<VisualSearch.Hit> hits=List.of();private volatile boolean closed;
 public PhotoCatalogController(PhotoCatalogView view,Window owner,Consumer<String> opener){this(view,owner,opener,WebcamSource::open);}
 public PhotoCatalogController(PhotoCatalogView view,Window owner,Consumer<String> opener,CameraDialog.Provider cameraProvider){this.view=view;this.owner=owner;this.opener=opener;this.cameraProvider=cameraProvider;view.actions(()->render(0),this::load,this::upload,this::camera,this::setup,()->{hits=List.of();view.photo(null);view.status("Photo filter cleared. Search applies to the curated catalog.");render(0);},p->PhotoComparisonDialog.show(owner,p,root,opener),id->{if(!saved.remove(id))saved.add(id);render(0);},this::render);}
 public void load(){view.busy(true);view.status("Validating original source rows and cached image hashes…");worker.submit(()->{try{var config=DatasetConfig.load();PhotoCatalogDao dao=config.storage().equals("mysql")?new JdbcPhotoCatalogDao(new ConnectionFactory(AppConfig.load()),root):new JsonPhotoCatalogDao(root);var data=dao.load();if(vision!=null){vision.close();vision=null;}Platform.runLater(()->{if(!closed){service=new CatalogService(data);hits=List.of();view.photo(null);view.loaded(data,config.storage().equals("mysql")?"MySQL":"local JSON");view.busy(false);view.status("Ready. Every card has 2+ genuine recorded prices. Photo search is local and optional.");render(0);}});}catch(Exception e){Platform.runLater(()->{if(!closed){service=null;view.error("Could not load the photo catalog. Keep datasets/promap intact; for MySQL run photo-catalog-schema.sql and PhotoCatalogImportMain. "+(e instanceof IllegalArgumentException?e.getMessage():"Check configuration/credentials. No fallback was used."));}});}});}
 private void render(int page){if(service==null)return;var rows=service.search(view.query(),view.category(),view.store(),view.three(),view.watchOnly(),saved,view.sort());Map<String,Double> scores=new HashMap<>();for(var hit:hits)scores.put(hit.product().id(),hit.similarity());if(!hits.isEmpty())rows=rows.stream().filter(p->scores.containsKey(p.id())).sorted(Comparator.<PhotoProduct>comparingDouble(p->scores.get(p.id())).reversed()).toList();view.page(rows,scores,Set.copyOf(saved),page,root);}
 public void upload(){if(service==null)return;FileChooser chooser=new FileChooser();chooser.setTitle("Choose a product photo — processed locally");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Product photos (JPG / PNG)","*.jpg","*.jpeg","*.png"));var file=chooser.showOpenDialog(owner);if(file!=null)searchPhoto(file.toPath());}
 public void searchPhoto(Path image){if(service==null)return;if(!consentModel())return;view.busy(true);worker.submit(()->{try{process(VisualSearch.read(image));}catch(Exception e){failedPhoto(e);}});}
 private void camera(){if(service!=null)CameraDialog.show(owner,cameraProvider,image->{if(consentModel()){view.busy(true);worker.submit(()->{try{process(image);}catch(Exception e){failedPhoto(e);}});}});}
 private boolean consentModel(){if(ModelFiles.present())return true;Alert ask=new Alert(Alert.AlertType.CONFIRMATION,"Automatic photo search needs a one-time 89 MB CLIP model download. It then runs on your computer through Java. Your image is never uploaded. Download now?",ButtonType.OK,ButtonType.CANCEL);ask.initOwner(owner);ask.setHeaderText("Set up offline photo search");return ask.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
 private void status(String text){Platform.runLater(()->{if(!closed)view.status(text);});}
 private void ensureVision()throws Exception{if(vision!=null)return;Path model=ModelFiles.install(this::status);VisualSearch created=new VisualSearch(model);try{created.index(service.products(),root,this::status);vision=created;}catch(Exception e){created.close();throw e;}}
 private void process(BufferedImage image)throws Exception{ensureVision();status("Comparing your photo locally against the reference catalog…");var result=vision.search(image,5);var preview=SwingFXUtils.toFXImage(image,null);Platform.runLater(()->{if(!closed){hits=result;view.resetFilters();view.photo(preview);view.busy(false);view.status("Top visual suggestions only. Similarity is not identity confidence. Confirm the exact product/variant; use Clear photo to restore the catalog.");render(0);}});}
 private void setup(){if(service==null||!consentModel())return;view.busy(true);worker.submit(()->{try{ensureVision();Platform.runLater(()->{if(!closed){view.busy(false);view.status("Offline photo model and reference index ready. Use Camera or Upload photo.");render(0);}});}catch(Exception e){failedPhoto(e);}});}
 private void failedPhoto(Exception e){Platform.runLater(()->{if(!closed){view.busy(false);view.status("Photo search did not complete. Check image format, model download/hash and available memory. No product identity was confirmed. "+(e instanceof IllegalArgumentException?e.getMessage():"You can still use text search."));render(0);}});}
 public void close(){closed=true;worker.shutdownNow();Thread closer=new Thread(()->{try{if(worker.awaitTermination(60,TimeUnit.SECONDS)){VisualSearch active=vision;if(active!=null)active.close();}}catch(Exception ignored){}},"tulita-vision-close");closer.setDaemon(true);closer.start();}
}
