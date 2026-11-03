package tulita.photocatalog.view;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Window;
import tulita.photocatalog.model.PhotoProduct;
import tulita.photocatalog.model.PhotoProduct.Offer;
import tulita.photocatalog.service.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import static tulita.view.DashboardView.label;
/** Same-variant comparison sheet with a recorded-price chart and source evidence. */
public final class PhotoComparisonDialog{
 private record Row(String store,Offer offer){}
 public static void show(Window owner,PhotoProduct p,Path root,Consumer<String> opener){
  Dialog<Void> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Tulita · "+p.name());
  dialog.getDialogPane().setId("catalog-comparison-dialog");
  dialog.getDialogPane().getStylesheets().add(PhotoComparisonDialog.class.getResource("/styles/tulita.css").toExternalForm());
  dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.setResizable(true);
  VBox body=new VBox(16);body.setPadding(new Insets(20));body.setPrefWidth(1020);body.getStyleClass().add("comparison-dialog");
  ImageView hero=new ImageView(new Image(CatalogValidator.safe(root,p.photos().get(0).path()).toUri().toString(),200,165,true,true));
  Label title=label(p.name(),"dialog-title");
  Label chips=label(p.category()+"  ·  "+p.offers().size()+" retailer"+(p.offers().size()==1?"":"s")+"  ·  model "+(p.model().equals("Not provided")?"not supplied":p.model()),"eyebrow");
  Label variant=label(p.variant(),"status");variant.setWrapText(true);variant.setMaxWidth(800);
  VBox head=new VBox(7,chips,title,variant);
  head.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(head,Priority.ALWAYS);
  HBox header=new HBox(20,hero,head);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("dialog-hero");
  body.getChildren().add(header);
  VBox low=kpi("LOWEST RECORDED",CatalogService.money(p.lowest(),p.currency()),"kpi-good");
  VBox high=kpi("HIGHEST RECORDED",CatalogService.money(CatalogService.highest(p),p.currency()),"");
  VBox spread=kpi("SPREAD",CatalogService.money(CatalogService.spread(p),p.currency()),CatalogService.spread(p).signum()==0?"":"kpi-warn");
  VBox stores=kpi("RETAILERS",Integer.toString(p.offers().size()),"");
  HBox kpis=new HBox(12,low,high,spread,stores);for(Node n:kpis.getChildren())HBox.setHgrow(n,Priority.ALWAYS);
  body.getChildren().add(kpis);
  VBox chartCard=new VBox(8,label("RECORDED ITEM PRICE BY RETAILER","eyebrow"),chart(p));
  Label chartNote=label("One recorded observation per retailer. This source supplies no capture date and no repeat observations, so a price-over-time trend cannot be drawn without inventing data.","chart-note");
  chartNote.setWrapText(true);chartCard.getChildren().add(chartNote);chartCard.getStyleClass().add("chart-card");
  body.getChildren().add(chartCard);
  Label caution=label("HISTORICAL USD OBSERVATIONS  ·  Capture dates, current stock, ratings, MRP, delivery and final totals are unknown. USD context comes from US retailer listings; no INR conversion. A smaller recorded item amount is not a current best delivered deal.","history-notice");
  caution.setWrapText(true);caution.setMaxWidth(1080);caution.setMinHeight(Region.USE_PREF_SIZE);
  body.getChildren().add(caution);
  TableView<Row> table=new TableView<>();table.setId("catalog-comparison-table");table.setPrefHeight(235);
  table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
  col(table,"Store",95,r->r.store());
  col(table,"Original source title",330,r->r.offer()==null?"No reviewed observation":r.offer().title());
  col(table,"Recorded item price",150,r->r.offer()==null?"—":CatalogService.money(r.offer().price(),r.offer().currency()));
  col(table,"Capture date",105,r->r.offer()==null?"—":"Unknown");
  col(table,"Rating",105,r->r.offer()==null?"—":"Not in source");
  col(table,"Price note",150,r->r.offer()==null?"Missing source":r.offer().price().compareTo(p.lowest())==0?"Lowest recorded item":"Historical item amount");
  TableColumn<Row,Void> details=new TableColumn<>("Evidence");details.setPrefWidth(105);
  details.setCellFactory(c->new TableCell<>(){
   final Button button=new Button("View source");
   {button.getStyleClass().add("link-button");button.setOnAction(e->{Row row=getTableRow().getItem();if(row!=null&&row.offer()!=null)source(owner,row.offer(),opener);});}
   @Override protected void updateItem(Void ignored,boolean empty){super.updateItem(ignored,empty);var row=getTableRow()==null?null:getTableRow().getItem();setGraphic(empty||row==null||row.offer()==null?null:button);}
  });
  table.getColumns().add(details);
  for(String store:CatalogValidator.STORES)table.getItems().add(new Row(store,p.offers().stream().filter(o->o.store().equals(store)).findFirst().orElse(null)));
  body.getChildren().add(table);
  FlowPane photos=new FlowPane(12,12);Set<String> shown=new HashSet<>();
  for(var photo:p.photos())if(shown.add(photo.store())){
   ImageView image=new ImageView(new Image(CatalogValidator.safe(root,photo.path()).toUri().toString(),132,110,true,true));
   VBox cell=new VBox(5,image,label(photo.store()+" reference photo","muted"));cell.getStyleClass().add("photo-cell");photos.getChildren().add(cell);
  }
  body.getChildren().add(photos);
  TextArea review=new TextArea("SOURCE-REVIEWED, NOT INDEPENDENTLY VERIFIED ONLINE\n"+p.reviewNote()+"\n\nRatings and review counts are not part of this source dataset, so none are displayed rather than invented.");
  review.setWrapText(true);review.setEditable(false);review.setPrefRowCount(4);
  body.getChildren().add(review);
  ScrollPane scroll=new ScrollPane(body);scroll.setFitToWidth(true);scroll.setPrefHeight(640);scroll.getStyleClass().add("dialog-scroll");
  dialog.getDialogPane().setContent(scroll);dialog.show();
 }
 private static VBox kpi(String caption,String value,String extra){
  Label cap=label(caption,"kpi-caption");Label val=label(value,"kpi-value");VBox box=new VBox(2,cap,val);
  box.getStyleClass().addAll("kpi-box",extra);box.setMaxWidth(Double.MAX_VALUE);return box;
 }
 private static BarChart<String,Number> chart(PhotoProduct p){
  CategoryAxis x=new CategoryAxis();NumberAxis y=new NumberAxis();
  x.setLabel("Retailer");y.setLabel("Recorded item price (USD)");
  BarChart<String,Number> chart=new BarChart<>(x,y);chart.setAnimated(false);chart.setLegendVisible(false);
  chart.setPrefHeight(260);chart.setCategoryGap(38);chart.setBarGap(4);chart.getStyleClass().add("price-chart");
  XYChart.Series<String,Number> series=new XYChart.Series<>();
  for(var o:p.offers())series.getData().add(new XYChart.Data<>(o.store(),o.price()));
  chart.getData().add(series);
  for(var data:series.getData()){
   if(data.getNode()!=null){
    data.getNode().getStyleClass().add("bar-"+data.getXValue().toLowerCase(Locale.ROOT));
    if(new java.math.BigDecimal(data.getYValue().toString()).compareTo(p.lowest())==0)data.getNode().getStyleClass().add("bar-lowest");
   }
  }
  return chart;
 }
 private static void source(Window owner,Offer o,Consumer<String> opener){
  Dialog<Void> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle(o.store()+" · Original source record");
  dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
  TextArea text=new TextArea(o.title()+"\n\n"+CatalogService.money(o.price(),o.currency())+" · capture date unknown · rating not in source\n"+o.url()+"\n\nEvidence: "+o.sourceFile()+" · CSV record "+o.sourceRow()+" · side "+o.sourceSide()+"\nSHA-256: "+o.sourceRowSha256()+"\n\nSpecifications (unaltered source text):\n"+o.specifications()+"\n\nDescription (unaltered source text):\n"+o.description());
  text.setEditable(false);text.setWrapText(true);text.setPrefSize(760,450);
  Button visit=new Button("Open historical retailer URL");visit.getStyleClass().add("primary-button");
  visit.setOnAction(e->opener.accept(o.url()));
  dialog.getDialogPane().setContent(new VBox(10,text,visit));dialog.show();
 }
 private static void col(TableView<Row> table,String name,int width,java.util.function.Function<Row,String> f){
  TableColumn<Row,String> c=new TableColumn<>(name);c.setPrefWidth(width);
  c.setCellValueFactory(v->new javafx.beans.property.SimpleStringProperty(f.apply(v.getValue())));
  c.setCellFactory(v->new TableCell<>(){@Override protected void updateItem(String s,boolean empty){super.updateItem(s,empty);setText(empty?null:s);setTooltip(empty?null:new Tooltip(s));}});
  table.getColumns().add(c);
 }
}
