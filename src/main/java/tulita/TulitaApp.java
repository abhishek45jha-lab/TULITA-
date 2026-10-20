package tulita;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import tulita.controller.DashboardController;
import tulita.view.DashboardView;
import tulita.database.AppConfig;
import tulita.dataset.DatasetController;
import tulita.dataset.view.DatasetView;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import java.util.Objects;

public final class TulitaApp extends Application {
    private AutoCloseable controller;
    @Override public void start(Stage stage) {
        try {
            AppConfig config=AppConfig.load();
            Parent view;
            Runnable load;
            if(config.mode().equals("catalog")){
                var catalogView=new tulita.photocatalog.view.PhotoCatalogView();
                var c=new tulita.photocatalog.PhotoCatalogController(catalogView,stage,url->getHostServices().showDocument(url));
                controller=c;view=catalogView;load=c::load;
            } else if(config.mode().equals("dataset") || config.mode().equals("multistore")) {
                DatasetView datasetView=new DatasetView(config.mode().equals("multistore"));
                DatasetController c=new DatasetController(datasetView,stage,url->getHostServices().showDocument(url),config.mode().equals("multistore"));
                controller=c; view=datasetView; load=c::load;
            } else {
                DashboardView demoView=new DashboardView();
                DashboardController c=new DashboardController(demoView,stage,url->getHostServices().showDocument(url));
                controller=c; view=demoView; load=c::load;
            }
            Scene scene=new Scene(view,1280,900);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles/tulita.css")).toExternalForm());
            stage.setTitle("Tulita — Smart E-Commerce Price Comparison"); stage.setMinWidth(1060); stage.setMinHeight(700); stage.setScene(scene); stage.show(); load.run();
        } catch(Exception e) {
            Alert error=new Alert(Alert.AlertType.ERROR,"Configuration could not be read. Check application.properties and TULITA_MODE (catalog, multistore, dataset, demo or mysql).");
            error.setHeaderText("Tulita setup required"); error.showAndWait(); javafx.application.Platform.exit();
        }
    }
    @Override public void stop() throws Exception { if(controller!=null)controller.close(); }

    public static void main(String[] args) { launch(args); }
}
