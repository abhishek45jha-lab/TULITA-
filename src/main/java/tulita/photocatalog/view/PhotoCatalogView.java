package tulita.photocatalog.view;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import tulita.photocatalog.model.PhotoProduct;
import tulita.photocatalog.service.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import static tulita.view.DashboardView.label;
/** Redesigned photo-catalog workspace: dark rail, KPI tiles and price-spread cards. */
public final class PhotoCatalogView extends BorderPane{
 public static final int PAGE_SIZE=9;
 private final TextField query=new TextField();
 private final ComboBox<String> category=new ComboBox<>(),store=new ComboBox<>(),sort=new ComboBox<>();
 private final CheckBox three=new CheckBox("3+ stores only");
 private final Button upload=new Button("⌁  Upload photo"),camera=new Button("◉  Camera"),setup=new Button("Set up photo search"),reload=new Button("↻  Reload"),clear=new Button("Clear photo"),prev=new Button("←  Prev"),next=new Button("Next  →"),discover=new Button("Discover"),saved=new Button("Watchlist");
 private final Label count=label("Loading source-reviewed catalog…","section-title"),status=label("","muted"),pageLabel=label("","muted"),source=label("","muted");
 private final Label statProducts=new Label("—"),statPrices=new Label("—"),statPhotos=new Label("—"),statThree=new Label("—");
 private final Label railProducts=new Label("—"),railStores=new Label("—"),railCategories=new Label("—");
 private final FlowPane grid=new FlowPane(20,20);
 private final VBox photoPane=new VBox(8);
 private final ImageView queryPreview=new ImageView();
 private Runnable filter=()->{},onReload=()->{},onUpload=()->{},onCamera=()->{},onSetup=()->{},onClear=()->{};
 private Consumer<PhotoProduct> compare=p->{};private Consumer<String> watch=id->{};private IntConsumer onPage=p->{};private boolean busy=true,watchOnly;private int page;
 public PhotoCatalogView(){
  getStyleClass().add("app");
  setLeft(rail());
  VBox main=new VBox(20);main.getStyleClass().add("workspace");
  main.setPadding(new Insets(24,32,30,32));
  main.getChildren().addAll(crumb(),hero(),notice(),toolbar(),photoPane,resultsHeader(),pager(),grid,footer());
  ScrollPane scroll=new ScrollPane(main);scroll.setFitToWidth(true);scroll.getStyleClass().add("main-scroll");
  scroll.viewportBoundsProperty().addListener((o,a,b)->resize(b.getWidth()-64));
  setCenter(scroll);
  busy(true);
 }
 private Node crumb(){Label path=label("Discover  /  Photo-enabled catalog","crumb");Label live=label("OFFLINE PHOTO SEARCH","pill-soft");Region space=new Region();HBox.setHgrow(space,Priority.ALWAYS);HBox box=new HBox(12,path,space,live);box.setAlignment(Pos.CENTER_LEFT);return box;}
 private Node hero(){
  Label kicker=label("SOURCE-REVIEWED · GENUINE HISTORICAL PRICES","hero-kicker");
  Label title=label("One product.\nA real comparison.","hero-title");
  Label subtitle=label("Compare Smart. Shop Better. Save More.","hero-subtitle");
  Label copy=label("Curated same-model, same-variant groups from Amazon, Walmart and Target — with the original recorded amounts, cached photos and evidence trail.","hero-copy");
  copy.setWrapText(true);copy.setMaxWidth(430);
  VBox text=new VBox(10,kicker,title,subtitle,copy);HBox.setHgrow(text,Priority.ALWAYS);
  HBox tiles=new HBox(10,tile(statProducts,"PRODUCTS"),tile(statPrices,"PRICES"),tile(statPhotos,"PHOTOS"),tile(statThree,"3-STORE"));
  tiles.setAlignment(Pos.BOTTOM_RIGHT);tiles.setMaxHeight(Region.USE_PREF_SIZE);
  HBox hero=new HBox(28,text,tiles);hero.setAlignment(Pos.CENTER_LEFT);hero.getStyleClass().add("hero");
  return hero;
 }
 private VBox tile(Label value,String caption){
  value.getStyleClass().add("kpi-value");Label cap=label(caption,"kpi-caption");
  VBox box=new VBox(3,value,cap);box.getStyleClass().add("kpi-tile");box.setMinWidth(96);box.setPrefWidth(96);box.setMaxHeight(Region.USE_PREF_SIZE);box.setAlignment(Pos.CENTER_LEFT);return box;
 }
 private Node notice(){Label notice=label("HISTORICAL, NOT LIVE  ·  Original US retailer observations. Capture dates, current stock, ratings, shipping and delivered totals are unknown. USD is the dataset’s US-store context, not an INR conversion.","history-notice");notice.setWrapText(true);notice.setMaxWidth(Double.MAX_VALUE);return notice;}
 private Node toolbar(){
  query.setId("catalog-search");query.setPromptText("Search a product, brand or model…  e.g. 58149, Vitamix, LEGO");query.getStyleClass().add("search-field");HBox.setHgrow(query,Priority.ALWAYS);
  query.textProperty().addListener((o,a,b)->{if(!busy)filter.run();});query.setOnAction(e->filter.run());
  upload.setId("catalog-upload");camera.setId("catalog-camera");setup.setId("catalog-setup");reload.setId("catalog-reload");clear.setId("catalog-clear-photo");
  upload.getStyleClass().add("primary-button");camera.getStyleClass().add("ghost-button");reload.getStyleClass().add("ghost-button");setup.getStyleClass().add("secondary-button");
  upload.setOnAction(e->onUpload.run());camera.setOnAction(e->onCamera.run());setup.setOnAction(e->onSetup.run());reload.setOnAction(e->onReload.run());clear.setOnAction(e->onClear.run());
  HBox search=new HBox(10,query,upload,camera,reload);search.setAlignment(Pos.CENTER_LEFT);
  category.setId("catalog-category");store.setId("catalog-store");sort.setId("catalog-sort");three.setId("catalog-three");
  category.setPrefWidth(190);store.setPrefWidth(150);
  sort.getItems().addAll("Product name","Lowest recorded item price","Most stores");sort.getSelectionModel().selectFirst();
  for(var box:List.of(category,store,sort))box.setOnAction(e->{if(!busy)filter.run();});
  three.setOnAction(e->filter.run());three.getStyleClass().add("filter-check");
  Label filtersLabel=label("FILTER","side-heading");
  HBox filters=new HBox(10,filtersLabel,category,store,sort,three);filters.setAlignment(Pos.CENTER_LEFT);
  VBox card=new VBox(14,search,filters);card.getStyleClass().add("toolbar-card");return card;
 }
 private Node resultsHeader(){
  count.setId("catalog-count");status.setId("catalog-status");status.setWrapText(true);source.setWrapText(true);
  queryPreview.setFitWidth(190);queryPreview.setFitHeight(130);queryPreview.setPreserveRatio(true);
  photoPane.setVisible(false);photoPane.setManaged(false);
  VBox labels=new VBox(6,label("Your photo — likely matches, not verified identity","section-title"),label("Confirm brand, model, size/colour, pack and condition. Similarity scores are not probabilities or identity guarantees.","muted"),clear);
  HBox preview=new HBox(16,queryPreview,labels);preview.setAlignment(Pos.CENTER_LEFT);
  photoPane.getChildren().add(preview);photoPane.getStyleClass().add("photo-panel");
  VBox head=new VBox(6,count,source,status);return head;
 }
 private Node pager(){
  prev.setId("catalog-previous");next.setId("catalog-next");
  prev.getStyleClass().add("pager-button");next.getStyleClass().add("pager-button");
  prev.setOnAction(e->onPage.accept(page-1));next.setOnAction(e->onPage.accept(page+1));
  Region space=new Region();HBox.setHgrow(space,Priority.ALWAYS);
  HBox pager=new HBox(10,setup,space,prev,pageLabel,next);pager.setAlignment(Pos.CENTER_LEFT);pager.getStyleClass().add("pager-bar");return pager;
 }
 private Node footer(){
  Label footer=label("ProMap / ProMapMulti curated subset · 79 reviewed groups · every card has at least two genuine priced source records · photos are cached reference assets · visual suggestions never create store offers · ratings are not supplied by this dataset.","footer");
  footer.setWrapText(true);return footer;
 }
 private Node rail(){
  VBox rail=new VBox(6);rail.getStyleClass().add("rail");rail.setPrefWidth(248);rail.setMinWidth(238);
  HBox brand=new HBox(10,label("t.","brand-mark"),label("tulita","brand"));brand.setAlignment(Pos.CENTER_LEFT);
  Label tagline=label("COMPARE SMART. SHOP BETTER.","side-tagline");
  discover.getStyleClass().addAll("nav-button","nav-active");saved.getStyleClass().add("nav-button");
  discover.setOnAction(e->{watchOnly=false;filter.run();});saved.setOnAction(e->{watchOnly=true;filter.run();});
  Label glance=label("CATALOG AT A GLANCE","rail-heading");
  VBox stats=new VBox(11,railStat(railProducts,"reviewed products"),railStat(railStores,"genuine recorded prices"),railStat(railCategories,"categories covered"));
  stats.getStyleClass().add("rail-stats");
  Region filler=new Region();VBox.setVgrow(filler,Priority.ALWAYS);
  Label note=label("Original USD amounts.\nNo live-price claim.\nRatings not provided.\n\nPhotos and queries stay on this computer.\nConfirm the exact variant.","rail-note");
  note.setWrapText(true);
  rail.getChildren().addAll(brand,tagline,new Separator(),discover,saved,glance,stats,filler,note,label("SESSION-ONLY WATCHLIST","rail-heading"));
  return rail;
 }
 private HBox railStat(Label value,String text){value.getStyleClass().add("rail-value");Label cap=label(text,"rail-caption");HBox row=new HBox(8,value,cap);row.setAlignment(Pos.BASELINE_LEFT);return row;}
 private void resize(double width){
  int cols=Math.max(1,Math.min(3,(int)((width+20)/316)));
  double cardWidth=Math.max(270,Math.floor((width-(cols-1)*20)/cols)-1);
  grid.setPrefWrapLength(width);
  for(Node n:grid.getChildren())if(n instanceof Region r){r.setMinWidth(cardWidth);r.setPrefWidth(cardWidth);r.setMaxWidth(cardWidth);}
 }
 public void actions(Runnable filter,Runnable reload,Runnable upload,Runnable camera,Runnable setup,Runnable clear,Consumer<PhotoProduct> compare,Consumer<String> watch,IntConsumer page){
  this.filter=filter;onReload=reload;onUpload=upload;onCamera=camera;onSetup=setup;onClear=clear;this.compare=compare;this.watch=watch;onPage=page;
 }
 public void loaded(List<PhotoProduct> products,String storage){
  category.getItems().setAll("All categories");category.getItems().addAll(products.stream().map(PhotoProduct::category).distinct().sorted().toList());category.getSelectionModel().selectFirst();
  store.getItems().setAll("All stores");store.getItems().addAll(products.stream().flatMap(p->p.offers().stream()).map(PhotoProduct.Offer::store).distinct().sorted().toList());store.getSelectionModel().selectFirst();
  int prices=products.stream().mapToInt(p->p.offers().size()).sum(),photos=products.stream().mapToInt(p->p.photos().size()).sum(),threeStore=(int)products.stream().filter(p->p.offers().size()>=3).count();
  statProducts.setText(Integer.toString(products.size()));statPrices.setText(Integer.toString(prices));statPhotos.setText(Integer.toString(photos));statThree.setText(Integer.toString(threeStore));
  railProducts.setText(Integer.toString(products.size()));railStores.setText(Integer.toString(prices));railCategories.setText(Long.toString(products.stream().map(PhotoProduct::category).distinct().count()));
  source.setText(products.size()+" curated products · "+prices+" source prices · "+threeStore+" three-store products · "+storage.toUpperCase(Locale.ROOT)+" · All displayed products have 2+ priced observations");
 }
 public void busy(boolean value){busy=value;for(Node n:List.of(query,category,store,sort,three,upload,camera,setup,reload,clear,prev,next))n.setDisable(value);}
 public void status(String text){status.setText(text);}
 public void error(String text){
  busy=true;for(Node n:List.of(query,category,store,sort,three,upload,camera,setup,clear,prev,next))n.setDisable(true);reload.setDisable(false);
  status.setText(text);count.setText("Catalog setup required — no fallback used");grid.getChildren().clear();
  for(Label value:List.of(statProducts,statPrices,statPhotos,statThree))value.setText("—");
  railProducts.setText("unavailable");railStores.setText("unavailable");railCategories.setText("unavailable");
 }
 public String query(){return query.getText();}
 public String category(){return category.getValue()==null?"All categories":category.getValue();}
 public String store(){return store.getValue()==null?"All stores":store.getValue();}
 public int sort(){return Math.max(0,sort.getSelectionModel().getSelectedIndex());}
 public boolean three(){return three.isSelected();}
 public boolean watchOnly(){return watchOnly;}
 public void resetFilters(){boolean old=busy;busy=true;query.clear();category.getSelectionModel().selectFirst();store.getSelectionModel().selectFirst();three.setSelected(false);watchOnly=false;busy=old;}
 public void photo(Image image){queryPreview.setImage(image);photoPane.setVisible(image!=null);photoPane.setManaged(image!=null);}
 public void page(List<PhotoProduct> rows,Map<String,Double> scores,Set<String> saved,int requested,Path root){
  int pages=Math.max(1,(rows.size()+PAGE_SIZE-1)/PAGE_SIZE);page=Math.min(Math.max(0,requested),pages-1);
  count.setText((scores.isEmpty()?(watchOnly?"Saved products":"Same product · compared across stores"):"Likely photo matches — confirm before comparing")+" · "+rows.size()+" results");
  pageLabel.setText("Page "+(page+1)+" / "+pages);prev.setDisable(page==0);next.setDisable(page+1>=pages);
  grid.getChildren().clear();
  for(var p:rows.subList(page*PAGE_SIZE,Math.min((page+1)*PAGE_SIZE,rows.size())))grid.getChildren().add(card(p,scores.get(p.id()),saved,root));
  if(rows.isEmpty()){Label empty=label("No matching reviewed products. Clear filters or your photo. This finite catalog cannot identify every object.","status");empty.setWrapText(true);grid.getChildren().add(empty);}
  var scroll=(ScrollPane)getCenter();resize(scroll.getViewportBounds().getWidth()-64);
 }
 private VBox card(PhotoProduct p,Double score,Set<String> saved,Path root){
  VBox box=new VBox(0);box.setId("catalog-product-"+p.id());box.getStyleClass().add("product-card");
  int stores=p.offers().size();
  ImageView image=new ImageView(new Image(CatalogValidator.safe(root,p.photos().get(0).path()).toUri().toString(),300,190,true,true));
  image.setFitWidth(300);image.setFitHeight(200);image.setPreserveRatio(true);
  image.fitWidthProperty().bind(box.widthProperty().subtract(2));
  StackPane art=new StackPane(image);art.getStyleClass().add("card-art");art.setPrefHeight(198);
  Label storeChip=label(stores+(stores>=3?" STORES":" STORES"),"chip-store");
  Button heart=new Button(saved.contains(p.id())?"♥":"♡");heart.setId("catalog-save-"+p.id());heart.getStyleClass().add("heart-button");heart.setOnAction(e->watch.accept(p.id()));
  StackPane.setAlignment(storeChip,Pos.TOP_LEFT);StackPane.setMargin(storeChip,new Insets(12));
  StackPane.setAlignment(heart,Pos.TOP_RIGHT);StackPane.setMargin(heart,new Insets(10));
  boolean tied=CatalogService.spread(p).signum()==0;
  StackPane.setAlignment(storeChip,Pos.TOP_LEFT);
  if(tied){Label tie=label("PRICES TIE","chip-tie");StackPane.setAlignment(tie,Pos.BOTTOM_LEFT);StackPane.setMargin(tie,new Insets(12));art.getChildren().addAll(tie);}
  art.getChildren().addAll(storeChip,heart);
  VBox offers=new VBox(4);
  var sorted=p.offers().stream().sorted(Comparator.comparing(PhotoProduct.Offer::price)).toList();
  for(var o:sorted){
   Circle dot=new Circle(4);dot.getStyleClass().addAll("store-dot",o.store().toLowerCase(Locale.ROOT));
   Label name=label(o.store(),"offer-store");Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);
   Label price=label(CatalogService.money(o.price(),o.currency()),"offer-price");
   if(o.price().compareTo(p.lowest())==0)price.getStyleClass().add("offer-lowest");
   HBox row=new HBox(7,dot,name,gap,price);row.setAlignment(Pos.CENTER_LEFT);offers.getChildren().add(row);
  }
  Label eyebrow=label(p.category().toUpperCase(Locale.ROOT),"card-category");
  Label name=label(p.name(),"product-name");name.setWrapText(true);name.setMinHeight(42);
  Label variant=label(p.variant(),"card-variant");variant.setWrapText(true);variant.setMinHeight(32);
  Label amount=label(CatalogService.money(p.lowest(),p.currency()),"price");
  Label caption=label("lowest recorded item price","card-caption");
  HBox priceRow=new HBox(9,amount,caption);priceRow.setAlignment(Pos.BASELINE_LEFT);
  Label saving=tied?label("Identical recorded prices at every retailer","badge-tie"):label("Spread "+CatalogService.money(CatalogService.spread(p),p.currency())+" · "+CatalogService.savingsPercent(p)+"% below highest","badge-save");
  saving.setWrapText(true);
  Button compareButton=new Button(score==null?"Compare this variant  →":"Confirm product & compare  →");
  compareButton.setId("catalog-compare-"+p.id());compareButton.getStyleClass().add("compare-button");compareButton.setMaxWidth(Double.MAX_VALUE);
  compareButton.setOnAction(e->compare.accept(p));
  VBox content=new VBox(8,eyebrow,name,variant,offers,saving,priceRow);
  if(score!=null)content.getChildren().add(label(String.format(Locale.ROOT,"Visual similarity %.3f · not identity confidence",score),"muted"));
  content.getChildren().add(compareButton);
  content.getStyleClass().add("card-content");
  box.getChildren().addAll(art,content);
  return box;
 }
}
