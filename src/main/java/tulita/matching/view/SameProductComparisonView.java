package tulita.matching.view;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import tulita.dataset.model.DatasetProduct;
import tulita.matching.model.MatchedProduct;
import tulita.matching.service.SameProductService;
import tulita.util.Money;
import java.util.*;
import java.util.function.Consumer;
import static tulita.view.DashboardView.label;

/** Fixed store slots; the only cross-store listings accepted are reviewed canonical memberships. */
public final class SameProductComparisonView {
    private record Row(String store,DatasetProduct listing){}
    public static void show(Window owner,DatasetProduct anchor,Optional<MatchedProduct> group,Consumer<DatasetProduct> details){
        Dialog<Void> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Tulita · Same-product comparison");dialog.getDialogPane().setId("same-product-dialog");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var css=SameProductComparisonView.class.getResource("/styles/tulita.css");if(css!=null)dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        VBox body=new VBox(12);body.setPadding(new Insets(18));body.setPrefWidth(1140);
        Label heading=label(group.map(g->g.product().name()).orElse(anchor.title()),"section-title");heading.setWrapText(true);heading.setMaxWidth(1080);heading.setMinHeight(Region.USE_PREF_SIZE);
        String state=group.map(g->{var c=g.product();return "REVIEWED IDENTITY LINKS · Model: "+c.model()+" · Variant: "+c.variantKey()+"\nSize: "+value(c.size())+" · Colour: "+value(c.color())+" · Capacity: "+value(c.capacity())+" · Pack: "+value(c.quantity())+" · Fit: "+value(c.gender());}).orElse("UNMAPPED SOURCE RECORD · No approved canonical identity. Other stores remain missing, even if similar titles exist.");
        Label variant=label(state,"status");variant.setWrapText(true);variant.setMaxWidth(1080);variant.setMinHeight(Region.USE_PREF_SIZE);variant.setId("same-product-identity");
        Label warning=label("Historical observations from different periods, not current offers. Delivery, stock and delivered total are unknown. Review evidence is supplied by a human; the app does not independently verify it online.","history-notice");warning.setWrapText(true);warning.setMaxWidth(1080);warning.setMinHeight(Region.USE_PREF_SIZE);
        TableView<Row> table=new TableView<>();table.setId("same-product-table");table.setPrefHeight(230);table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(table,"Store",85,r->r.store());column(table,"Source listing",230,r->r.listing()==null?"Not in reviewed group":r.listing().title());
        column(table,"Item price / MRP",140,r->r.listing()==null?"—":money(r.listing().price())+" / "+money(r.listing().mrp()));
        column(table,"Rating",85,r->r.listing()==null||r.listing().rating()==null?"Unknown":r.listing().rating().stripTrailingZeros().toPlainString());
        column(table,"Observed date",110,r->r.listing()==null?"—":r.listing().observedDate());
        column(table,"Delivery / final total",125,r->r.listing()==null?"—":"Both unknown");
        column(table,"Identity status",170,r->r.listing()==null?"Missing reviewed source":group.isPresent()?"Reviewed mapping":"Unmapped anchor only");
        TableColumn<Row,Void> action=new TableColumn<>("Source details");action.setPrefWidth(105);action.setCellFactory(c->new TableCell<>(){final Button button=new Button("View record");{button.setOnAction(e->{Row r=getTableRow().getItem();if(r!=null&&r.listing()!=null)details.accept(r.listing());});} @Override protected void updateItem(Void ignored,boolean empty){super.updateItem(ignored,empty);Row r=getTableRow()==null?null:getTableRow().getItem();setGraphic(empty||r==null||r.listing()==null?null:button);}});table.getColumns().add(action);
        for(String store:SameProductService.STORES)table.getItems().add(new Row(store,group.map(g->g.listings().get(store)).orElseGet(()->anchor.store().equals(store)?anchor:null)));
        body.getChildren().addAll(heading,variant,warning,table);
        if(group.isPresent()){
            var g=group.get();if(g.listings().size()>=2)g.listings().values().stream().map(DatasetProduct::price).filter(Objects::nonNull).min(java.math.BigDecimal::compareTo).ifPresent(min->{Label lowest=label("Lowest recorded item price in this reviewed group: "+Money.inr(min)+". This is NOT a current-price or delivered-cost winner.","muted");lowest.setWrapText(true);body.getChildren().add(lowest);});
            StringBuilder notes=new StringBuilder();for(String store:SameProductService.STORES){var link=g.reviews().get(store);if(link!=null)notes.append(store).append(" · ").append(link.recordId()).append(" · Reviewed ").append(link.reviewedOn()).append("\n").append(link.evidenceUrl()).append("\n").append(link.reviewNote()).append("\n\n");}
            TextArea evidence=new TextArea(notes.toString());evidence.setEditable(false);evidence.setWrapText(true);evidence.setPrefRowCount(5);evidence.setId("same-product-evidence");body.getChildren().addAll(label("Review evidence (not independently checked online)","muted"),evidence);
        }
        dialog.getDialogPane().setContent(body);dialog.setResizable(true);dialog.show();
    }
    private static String money(java.math.BigDecimal p){return p==null?"Unknown":Money.inr(p);}
    private static String value(String s){return s.isBlank()?"Not recorded":s;}
    private static void column(TableView<Row> table,String name,int width,java.util.function.Function<Row,String> f){TableColumn<Row,String> col=new TableColumn<>(name);col.setPrefWidth(width);col.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(f.apply(v.getValue())));col.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String text,boolean empty){super.updateItem(text,empty);setText(empty?null:text);setTooltip(empty||text==null?null:new Tooltip(text));}});table.getColumns().add(col);}
}
