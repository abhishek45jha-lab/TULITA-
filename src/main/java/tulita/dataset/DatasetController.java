package tulita.dataset;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.stage.Window;
import javafx.util.Duration;
import tulita.dao.DataAccessException;
import tulita.database.*;
import tulita.dataset.dao.*;
import tulita.dataset.service.DatasetService;
import tulita.dataset.view.*;
import java.util.*;
import tulita.matching.service.SameProductService;
import tulita.matching.dao.*;
import tulita.matching.view.SameProductComparisonView;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class DatasetController implements AutoCloseable {
    private final DatasetView view;
    private final boolean multi;
    private final Window owner;
    private final Consumer<String> opener;
    private final Set<String> watched=new HashSet<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"tulita-dataset-worker");t.setDaemon(true);return t;});
    private final PauseTransition debounce=new PauseTransition(Duration.millis(180));
    private DatasetService service;
    private SameProductService matching;
    private volatile boolean closed;
    private long generation;
    private Future<?> pending;
    public DatasetController(DatasetView view,Window owner,Consumer<String> opener) {
        this(view,owner,opener,false);
    }
    public DatasetController(DatasetView view,Window owner,Consumer<String> opener,boolean multi) {
        this.view=view;this.multi=multi;this.owner=owner;this.opener=opener;
        view.exportAction(this::exportRecords);
        debounce.setOnFinished(e->search(0));
        view.actions(()->{generation++;view.searching();debounce.playFromStart();},this::load,
            p->{if(multi&&matching!=null)SameProductComparisonView.show(owner,p,matching.groupFor(p.recordId()),this::details);else if(!multi)details(p);},
            id->{toggle(id);search(view.page());},this::search);
    }
    private void details(tulita.dataset.model.DatasetProduct p){DatasetDetailView.show(owner,p,opener,watched.contains(p.recordId()),()->{toggle(p.recordId());search(view.page());});}
    private void toggle(String id){if(!watched.remove(id))watched.add(id);}
    public void load() {
        debounce.stop(); service=null; matching=null; long version=++generation; if(pending!=null)pending.cancel(true); view.showLoading();
        pending=worker.submit(()->{
            try {
                DatasetConfig config=DatasetConfig.load();
                DatasetDao dao=multi?(config.storage().equals("mysql")?new MultiJdbcDao(new ConnectionFactory(AppConfig.load())):new MultiCsvDao(MultiCsvDao.defaultPath())):(config.storage().equals("mysql")?new JdbcDatasetDao(new ConnectionFactory(AppConfig.load())):new CsvDatasetDao(config.csvPath()));
                DatasetService loaded=new DatasetService(dao.load());
                SameProductService reviewed=multi?new SameProductService(loaded.snapshot(),config.storage().equals("mysql")?new ReviewedJdbcDao(new ConnectionFactory(AppConfig.load())).load():new ReviewedCsvDao().load(ReviewedCsvDao.defaultPath())):null;
                List<String> categories=new ArrayList<>(loaded.categories());if(reviewed!=null)reviewed.groups().forEach(g->{if(!categories.contains(g.product().category()))categories.add(g.product().category());});Collections.sort(categories);
                Platform.runLater(()->{if(!closed&&version==generation){service=loaded;matching=reviewed;view.matching(reviewed);view.loaded(loaded.snapshot(),categories);search(0);}});
            }catch(Exception ex){Platform.runLater(()->{if(!closed&&version==generation)view.showError(ex instanceof DataAccessException||ex instanceof IllegalArgumentException||ex instanceof java.io.IOException?ex.getMessage():"Dataset configuration is invalid. Check dataset.storage and the CSV path; no fallback was used.");});}
        });
    }
    private void search(int page) {
        if(service==null||closed)return;
        if(pending!=null && !pending.isDone())pending.cancel(false);
        long version=++generation;
        String query=view.query(),category=view.category(),store=view.store(); var sort=view.sortOrder(); boolean watchOnly=view.watchOnly(),threeOnly=view.threeOnly(); Set<String> ids=Set.copyOf(watched);
        view.searching();
        pending=worker.submit(()->{
            try {
                var result=multi?matching.search(query,category,sort,watchOnly,ids,page,DatasetView.PAGE_SIZE,store,threeOnly):service.search(query,category,sort,watchOnly,ids,page,DatasetView.PAGE_SIZE,store);
                Platform.runLater(()->{if(!closed&&version==generation)view.showPage(result,ids);});
            }catch(RuntimeException ex){Platform.runLater(()->{if(!closed&&version==generation)view.showError("Could not filter the historical catalog. Reload the source and try again.");});}
        });
    }
    private void exportRecords(){
        if(service==null)return;
        javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();chooser.setTitle("Export original source IDs and SHA-256 fingerprints");chooser.setInitialFileName("record-index.csv");var file=chooser.showSaveDialog(owner);if(file==null)return;var snapshot=service.snapshot();
        worker.submit(()->{try{tulita.matching.ReviewToolsMain.export(snapshot,file.toPath());Platform.runLater(()->{if(!closed)new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,"Source index exported. No identity mappings were generated. See matching/README.md.").show();});}catch(Exception ex){Platform.runLater(()->{if(!closed)new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,"Could not write the source index. Check the selected folder's permissions.").show();});}});
    }
    @Override public void close(){closed=true;generation++;debounce.stop();worker.shutdownNow();}
}
