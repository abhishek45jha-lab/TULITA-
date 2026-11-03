package tulita.dataset.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import tulita.dataset.DatasetLinks;
import tulita.dataset.model.*;
import tulita.util.Money;
import java.util.function.Consumer;
import static tulita.view.DashboardView.label;

public final class DatasetDetailView {
    private DatasetDetailView() { }
    public static Dialog<Void> show(Window owner,DatasetProduct p,Consumer<String> opener,boolean saved,Runnable watch) {
        Dialog<Void> dialog=new Dialog<>(); dialog.initOwner(owner); dialog.setTitle("Tulita · Historical Flipkart record"); dialog.setResizable(true);
        DialogPane pane=dialog.getDialogPane(); pane.setId("dataset-detail"); pane.getStyleClass().add("comparison-dialog"); pane.getStylesheets().add(DatasetDetailView.class.getResource("/styles/tulita.css").toExternalForm()); pane.getButtonTypes().add(ButtonType.CLOSE);
        VBox content=new VBox(14); content.setPrefWidth(850); content.setPadding(new Insets(12));
        Label title=label(p.title(),"dialog-title"); title.setWrapText(true); title.setPrefWidth(810);
        Label warning=label("HISTORICAL RECORD · Observed "+p.observedDate()+". Not a current quote. Availability, shipping and final payable total are unknown.","history-notice"); warning.setWrapText(true); warning.setPrefWidth(810);
        GridPane data=new GridPane(); data.setHgap(26); data.setVgap(10);
        row(data,0,"Dataset price",p.price()==null?"Unknown":Money.inr(p.price())); row(data,1,"Original MRP",p.mrp()==null?"Unknown":Money.inr(p.mrp()));
        row(data,2,"Calculated discount",p.discount().map(v->v.toPlainString()+"%").orElse("Unknown / inconsistent source values"));
        row(data,3,"Recorded rating",p.rating()==null?"Unknown":p.rating().stripTrailingZeros().toPlainString()+" / 5");
        row(data,4,"Category",p.category()); row(data,5,"Brand",p.brand().isBlank()?"Unknown":p.brand()); row(data,6,p.store()+" product ID",p.productId()); row(data,7,"Source record ID",p.recordId());
        Button save=new Button(saved?"♥  Saved":"♡  Save record"); save.getStyleClass().add("secondary-button"); save.setOnAction(e->{watch.run();save.setText(save.getText().startsWith("♥")?"♡  Save record":"♥  Saved");});
        Button link=new Button("Open saved product link ↗"); link.getStyleClass().add("primary-button"); var safe=DatasetLinks.safeProductUrl(p.store(),p.url()); link.setDisable(safe.isEmpty()); link.setId("dataset-open-link");
        link.setOnAction(e->{
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"This old product URL may no longer work, and any current price can differ. Open the store in your default browser?",ButtonType.OK,ButtonType.CANCEL); confirm.initOwner(pane.getScene().getWindow()); confirm.setHeaderText("Leaving the historical catalog");
            if(confirm.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) try{opener.accept(safe.orElseThrow());}catch(RuntimeException ex){Alert a=new Alert(Alert.AlertType.ERROR,"Could not open the default browser.");a.initOwner(pane.getScene().getWindow());a.showAndWait();}
        });
        TextField url=new TextField(p.url()); url.setEditable(false); url.setAccessibleText("Original dataset URL");
        TextArea description=new TextArea(p.description()); description.setEditable(false); description.setWrapText(true); description.setPrefRowCount(5);
        TextArea specs=new TextArea(p.specifications()); specs.setEditable(false); specs.setWrapText(true); specs.setPrefRowCount(4);
        TabPane tabs=new TabPane(new Tab("Description / source excerpt",description),new Tab("Source specifications",specs)); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.setPrefHeight(170);
        Label identity=label("No variant identity has been inferred. Records remain separate even if names look similar. A title-similarity suggestion does not establish a same-variant cross-store deal.","muted"); identity.setWrapText(true); identity.setPrefWidth(810); identity.setMinHeight(36);
        Label source=label("Source: "+p.provenance().sourceUrl()+" · "+p.provenance().license()+" · "+p.provenance().dateNote(),"footer"); source.setWrapText(true); source.setPrefWidth(810); source.setMinHeight(28);
        content.getChildren().addAll(label("HISTORICAL DATASET / "+p.store().toUpperCase(),"eyebrow"),title,warning,data,new HBox(12,save,link),url,tabs,identity,source); ScrollPane scroller=new ScrollPane(content); scroller.setFitToWidth(true); scroller.setPrefViewportHeight(650); scroller.setMaxHeight(650); scroller.setPrefViewportWidth(875); scroller.getStyleClass().add("main-scroll"); pane.setContent(scroller); dialog.show(); return dialog;
    }
    private static void row(GridPane grid,int row,String key,String value){ grid.add(label(key,"muted"),0,row); Label v=label(value,"note-title"); v.setWrapText(true); v.setMaxWidth(590); grid.add(v,1,row); }
}
