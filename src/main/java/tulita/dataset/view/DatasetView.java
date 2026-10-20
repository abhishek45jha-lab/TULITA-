package tulita.dataset.view;

import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tulita.dataset.model.*;
import tulita.dataset.service.DatasetService;
import tulita.view.ProductArt;
import tulita.util.Money;
import java.util.*;
import java.util.function.*;
import static tulita.view.DashboardView.label;

/** Historical catalog: paginated records, never fabricated cross-store offers. */
public final class DatasetView extends BorderPane {
    public static final int PAGE_SIZE=12;
    private final boolean multi;
    private final ComboBox<String> stores=new ComboBox<>();
    private final CheckBox threeOnly=new CheckBox("Available in all 3 datasets (reviewed links)");
    private final Button export=new Button("Export source IDs");
    private Runnable onExport=()->{};
    private tulita.matching.service.SameProductService matching;
    private final TextField search=new TextField();
    private final ComboBox<String> categories=new ComboBox<>(), sort=new ComboBox<>();
    private final FlowPane cards=new FlowPane(18,18);
    private final VBox results=new VBox(14);
    private final Label count=label("Loading historical catalog…","section-title");
    private final Label source=label("PromptCloud · CC BY-SA 4.0 · Historical records, not live offers","muted");
    private final Label mode=label("HISTORICAL DATASET","mode-pill");
    private final Label progress=label("","muted"), pageLabel=label("","muted");
    private final Label savedCount=label("0","count-pill");
    private final Button previous=new Button("← Previous"), next=new Button("Next →"), reload=new Button("Reload source"), retry=new Button("Retry loading");
    private final Button discover=new Button("◈   Discover"), watchlist=new Button("♡   Watchlist");
    private final ScrollPane scroll;
    private Runnable onFilter=()->{}, onReload=()->{};
    private Consumer<DatasetProduct> onDetails=p->{};
    private Consumer<String> onWatch=id->{};
    private IntConsumer onPage=p->{};
    private int page;
    private boolean busy=true, watchOnly;

    public DatasetView(){this(false);}
    public DatasetView(boolean multi) {
        this.multi=multi;
        getStyleClass().add("app"); setLeft(sidebar());
        VBox main=new VBox(20); main.setPadding(new Insets(24,30,24,30));
        Region topSpace=new Region(); HBox.setHgrow(topSpace,Priority.ALWAYS);
        mode.setId("dataset-mode");
        HBox top=new HBox(12,label("Discover / Open dataset catalog","muted"),topSpace,mode); top.setAlignment(Pos.CENTER_LEFT);
        VBox heroText=new VBox(8,label("REAL CATALOG RECORDS. HONEST LIMITS.","hero-kicker"),label("More products. More possibilities.","hero-title"),label("Compare Smart. Shop Better. Save More.","hero-subtitle"));
        HBox hero=new HBox(heroText); hero.getStyleClass().add("hero"); hero.setAlignment(Pos.CENTER_LEFT);
        Label notice=label(multi?"MIXED HISTORICAL SNAPSHOTS · Flipkart 2015–2016 / Myntra 2021 / Amazon 2023. Only reviewed same-model + exact-variant groups are compared. No current-price or delivered-cost claim.":"HISTORICAL SNAPSHOT · Collected 2015–2016. Prices and links may be obsolete. Current availability and delivery charges are unknown.","history-notice"); notice.setWrapText(true); notice.setMaxWidth(Double.MAX_VALUE);
        search.setId("dataset-search"); search.setPromptText("Search the full catalog: shoes, Samsung, necklace, watch…"); search.getStyleClass().add("search-field"); HBox.setHgrow(search,Priority.ALWAYS);
        Button go=new Button("Search →"); go.setId("dataset-search-button"); go.getStyleClass().add("primary-button");
        go.setOnAction(e->filter()); search.setOnAction(e->filter()); search.textProperty().addListener((o,a,b)->filter());
        reload.getStyleClass().add("secondary-button"); reload.setOnAction(e->onReload.run());
        HBox searchbar=new HBox(10,search,go,reload); searchbar.setAlignment(Pos.CENTER_LEFT);
        categories.setId("dataset-category"); categories.setPrefWidth(240); categories.setOnAction(e->filter());
        sort.getItems().addAll("Lowest dataset price","Highest discount","Top rated"); sort.getSelectionModel().select(0); sort.setId("dataset-sort"); sort.setOnAction(e->filter()); sort.setPrefWidth(180);
        Region filtersSpace=new Region(); HBox.setHgrow(filtersSpace,Priority.ALWAYS);
        HBox filters=new HBox(10,label("Category","muted"),categories,filtersSpace,label("Sort by","muted"),sort); filters.setAlignment(Pos.CENTER_LEFT);
        stores.getItems().addAll("All stores","Amazon","Flipkart","Myntra"); stores.getSelectionModel().selectFirst(); stores.setId("dataset-store"); stores.setPrefWidth(130); stores.setOnAction(e->filter());
        if(multi)filters.getChildren().addAll(label("Store","muted"),stores);
        threeOnly.setId("same-product-three-only"); threeOnly.setSelected(true); threeOnly.setOnAction(e->filter());
        export.setId("export-source-ids"); export.getStyleClass().add("secondary-button"); export.setOnAction(e->onExport.run());
        if(multi)searchbar.getChildren().add(export);
        count.setId("dataset-count"); source.setWrapText(true); progress.setId("dataset-progress");
        previous.getStyleClass().add("secondary-button"); next.getStyleClass().add("secondary-button");
        previous.setId("dataset-previous"); next.setId("dataset-next"); pageLabel.setId("dataset-page");
        previous.setOnAction(e->onPage.accept(page-1)); next.setOnAction(e->onPage.accept(page+1));
        Region pagerSpace=new Region(); HBox.setHgrow(pagerSpace,Priority.ALWAYS);
        HBox pager=new HBox(12,progress,pagerSpace,previous,pageLabel,next); pager.setAlignment(Pos.CENTER_LEFT);
        cards.setId("dataset-grid"); results.getChildren().add(cards);
        Label footer=label(multi?"SOURCES: PromptCloud (Flipkart), Asaniczka (Amazon India sample), Nirokey / CrawlFeeds (Myntra). Per-source licences retained. Historical records only; no invented store prices.":"SOURCE: PromptCloud / Kaggle · CC BY-SA 4.0 · One Flipkart observation per record. No automatic variant merging or invented Amazon/Myntra prices. Artwork is illustrative.","footer"); footer.setWrapText(true);
        main.getChildren().addAll(top,hero,notice,searchbar,filters);
        if(multi)main.getChildren().add(threeOnly);
        main.getChildren().addAll(count,source,pager,results,footer);
        scroll=new ScrollPane(main); scroll.setFitToWidth(true); scroll.getStyleClass().add("main-scroll");
        scroll.viewportBoundsProperty().addListener((o,a,b)->resizeCards(b.getWidth()-60)); setCenter(scroll);
        retry.getStyleClass().add("primary-button"); retry.setId("dataset-retry"); retry.setOnAction(e->onReload.run());
        showLoading();
    }
    private Node sidebar() {
        VBox side=new VBox(12); side.setPrefWidth(208); side.setMinWidth(190); side.getStyleClass().add("sidebar");
        HBox brand=new HBox(9,label("t.","brand-mark"),label("tulita","brand")); brand.setAlignment(Pos.CENTER_LEFT);
        discover.getStyleClass().addAll("nav-button","nav-active"); watchlist.getStyleClass().add("nav-button");
        discover.setId("dataset-discover"); watchlist.setId("dataset-watchlist");
        discover.setOnAction(e->{watchOnly=false; navigation(); filter();}); watchlist.setOnAction(e->{watchOnly=true; navigation(); filter();});
        HBox saved=new HBox(5,watchlist,savedCount); saved.setAlignment(Pos.CENTER_LEFT);
        side.getChildren().addAll(brand,label("Compare smart. Shop better.","side-tagline"),new Separator(),discover,saved,label("QUICK CATEGORIES","side-heading"));
        for(String name:List.of("All categories","Clothing","Footwear","Mobiles & Accessories","Computers","Jewellery","Watches")) {
            Button b=new Button(name); b.getStyleClass().add("category-button"); b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e->{if(!busy && categories.getItems().contains(name)) categories.getSelectionModel().select(name);}); side.getChildren().add(b);
        }
        Region space=new Region(); VBox.setVgrow(space,Priority.ALWAYS);
        Label note=label(multi?"Amazon · Flipkart · Myntra\nReviewed identity links.\nHistorical, not live prices.":"20,000 source records.\nCaptured in 2015–2016.\nNot live Flipkart prices.","muted"); note.setWrapText(true);
        VBox noteBox=new VBox(8,label("Open-data catalog.","note-title"),note); noteBox.getStyleClass().add("sidebar-note");
        Label session=label("Watchlist is session-only.\nClose the app to reset it.","side-tagline"); session.setWrapText(true);
        side.getChildren().addAll(space,noteBox,session); return side;
    }
    public void actions(Runnable filter,Runnable load,Consumer<DatasetProduct> details,Consumer<String> watch,IntConsumer pageAction) { onFilter=filter; onReload=load; onDetails=details; onWatch=watch; onPage=pageAction; }
    private void filter() { if(!busy) onFilter.run(); }
    private void navigation() { discover.getStyleClass().remove("nav-active"); watchlist.getStyleClass().remove("nav-active"); (watchOnly?watchlist:discover).getStyleClass().add("nav-active"); }
    public void exportAction(Runnable action){onExport=action;}
    public boolean threeOnly(){return multi&&threeOnly.isSelected();}
    public void matching(tulita.matching.service.SameProductService service){matching=service;}
    public String store(){return multi?stores.getValue():"All stores";}
    public String query() { return search.getText(); }
    public String category() { return categories.getValue()==null ? "All categories" : categories.getValue(); }
    public DatasetService.Sort sortOrder() { return DatasetService.Sort.values()[Math.max(0,sort.getSelectionModel().getSelectedIndex())]; }
    public boolean watchOnly() { return watchOnly; }
    public int page() { return page; }
    public void showLoading() { busy=true; threeOnly.setDisable(true); export.setDisable(true); search.setDisable(true); categories.setDisable(true); sort.setDisable(true); reload.setDisable(true); previous.setDisable(true); next.setDisable(true); count.setText("Loading historical catalog…");
        ProgressIndicator spinner=new ProgressIndicator(); spinner.setMaxSize(35,35); results.getChildren().setAll(state(spinner,label("Reading and indexing the dataset on a background worker…","muted"))); }
    public void loaded(DatasetSnapshot data,List<String> availableCategories) {
        categories.getItems().setAll("All categories"); categories.getItems().addAll(availableCategories); categories.getSelectionModel().selectFirst();
        mode.setText("HISTORICAL · "+data.storage().toUpperCase(Locale.ROOT));
        source.setText(String.format(Locale.ROOT,"%,d records · %s · %s · %s to %s",data.products().size(),data.sourceTitle(),data.license(),date(data.firstObserved()),date(data.lastObserved())));
        if(multi)source.setText(String.format(Locale.ROOT,"%,d historical records · Amazon 20,000 · Flipkart 20,000 · Myntra 12,491 · Source dates/licences shown in record details",data.products().size()));
        threeOnly.setDisable(false);export.setDisable(false);
        if(multi&&matching!=null)source.setText(String.format(Locale.ROOT,"%,d historical source records · %,d reviewed variants · %,d mapped to all 3 stores · Not current availability",data.products().size(),matching.groups().size(),matching.threeStoreCount()));
        search.setDisable(false); categories.setDisable(false); sort.setDisable(false); reload.setDisable(false); busy=false;
    }
    private String date(String raw) { return raw.length()>=10 ? raw.substring(0,10) : "Unknown"; }
    public void searching() { progress.setText("Searching…"); previous.setDisable(true); next.setDisable(true); }
    public void showError(String message) { busy=true; threeOnly.setDisable(true); export.setDisable(true); search.setDisable(true); categories.setDisable(true); sort.setDisable(true); previous.setDisable(true); next.setDisable(true); reload.setDisable(false); count.setText("Historical catalog setup required"); mode.setText("SETUP REQUIRED"); Label text=label(message,"status"); text.setWrapText(true); results.getChildren().setAll(state(label("!","state-icon"),text,retry)); }
    private VBox state(Node...nodes) { VBox box=new VBox(18,nodes); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(40)); box.setMinHeight(230); box.getStyleClass().add("state-panel"); return box; }
    public void showPage(DatasetService.Page result,Set<String> watched) {
        page=result.page(); savedCount.setText(String.valueOf(watched.size())); progress.setText(multi?"One card per reviewed variant · Unmapped records are never auto-matched":"12 records per page · Dataset item prices, not delivered totals");
        count.setText((watchOnly?"Your historical watchlist":multi&&threeOnly()?"Same product across all three stores":"Explore the dataset")+String.format(Locale.ROOT," · %,d results",result.total()));
        pageLabel.setText(String.format(Locale.ROOT,"Page %,d / %,d",page+1,result.pages())); previous.setDisable(page==0); next.setDisable(page+1>=result.pages());
        cards.getChildren().clear();
        if(result.items().isEmpty()) {
            Label msg=label(multi&&threeOnly()?(matching!=null&&matching.threeStoreCount()==0?"No reviewed same-product variant is mapped to all 3 stores. These independent datasets do not establish a reliable 3-way match. Uncheck the filter to browse source records. To add genuine matches, export source IDs, review exact model/variant evidence, and follow matching/README.md. No similar products or invented prices will be substituted.":"No reviewed three-store variants match your filters. Try clearing the search or category."):watchOnly&&watched.isEmpty()?"Your watchlist is empty. Use ♡ to save a record.":"No matching records. Try fewer words or All categories.","status"); msg.setWrapText(true);
            Button clear=new Button("Clear search & category"); clear.getStyleClass().add("secondary-button"); clear.setOnAction(e->{search.clear(); categories.getSelectionModel().selectFirst(); filter();});
            results.getChildren().setAll(state(label("⌕","state-icon"),msg,clear));
        } else { result.items().forEach(p->cards.getChildren().add(card(p,watched))); results.getChildren().setAll(cards); resizeCards(scroll.getViewportBounds().getWidth()-60); }
    }
    private VBox card(DatasetProduct p,Set<String> watched) {
        VBox box=new VBox(10); box.getStyleClass().add("product-card"); box.setId("record-"+p.recordId());
        StackPane art=new StackPane(new ProductArt(p.art(),240,140)); art.getStyleClass().add("product-image");
        Label stamp=label(multi?(matching!=null&&matching.groupFor(p.recordId()).isPresent()?"REVIEWED SAME VARIANT":"UNMAPPED · "+p.store().toUpperCase(Locale.ROOT)):"DATASET RECORD","dataset-stamp"); StackPane.setAlignment(stamp,Pos.TOP_LEFT); StackPane.setMargin(stamp,new Insets(10)); art.getChildren().add(stamp);
        Button save=new Button(watched.contains(p.recordId())?"♥":"♡"); save.getStyleClass().add("heart-button"); save.setId("save-"+p.recordId()); save.setAccessibleText("Save or remove "+p.title()); save.setOnAction(e->onWatch.accept(p.recordId()));
        StackPane.setAlignment(save,Pos.TOP_RIGHT); StackPane.setMargin(save,new Insets(7)); art.getChildren().add(save);
        VBox body=new VBox(8); body.setPadding(new Insets(2,15,15,15));
        Label category=label(p.category().toUpperCase(Locale.ROOT),"card-category"); category.setTooltip(new Tooltip(p.category()));
        var group=matching==null?java.util.Optional.<tulita.matching.model.MatchedProduct>empty():matching.groupFor(p.recordId());
        String displayTitle=group.map(g->g.product().name()+" · "+g.product().variantKey()).orElse(p.title());
        Label title=label(displayTitle,"product-name"); title.setWrapText(true); title.setMinHeight(43); title.setPrefHeight(43); title.setTooltip(new Tooltip(displayTitle));
        Label brand=label(p.brand().isBlank()?"Brand not recorded":p.brand(),"muted");
        java.math.BigDecimal shownPrice=matching==null?p.price():matching.metric(p,0);
        HBox prices=new HBox(8,label(shownPrice==null?"Price unknown":Money.inr(shownPrice),"price")); prices.setAlignment(Pos.CENTER_LEFT);
        if(group.isEmpty())p.discount().ifPresent(d->prices.getChildren().add(label(d.toPlainString()+"% off","discount")));
        Label rating=label(group.isPresent()?"Lowest recorded item price · "+group.get().listings().size()+"/3 stores reviewed":"MRP "+(p.mrp()==null?"unknown":Money.inr(p.mrp()))+"  ·  "+(p.rating()==null?"Rating unknown":"★ "+p.rating().stripTrailingZeros().toPlainString()),"card-caption"); rating.setWrapText(true);
        Label observed=label(group.isPresent()?"Variant: "+group.get().product().variantKey()+" · Historical only":p.store()+" · Observed "+p.observedDate(),"muted"); observed.setWrapText(true);
        Button detail=new Button(multi?"Compare this exact variant    →":"View dataset record    →"); detail.getStyleClass().add("compare-button"); detail.setMaxWidth(Double.MAX_VALUE); detail.setId("detail-"+p.recordId()); detail.setOnAction(e->onDetails.accept(p));
        body.getChildren().addAll(category,title,brand,prices,rating,observed,detail);
        box.getChildren().addAll(art,body); return box;
    }
    private void resizeCards(double available) { if(available<=0)return; int columns=Math.max(1,Math.min(4,(int)((available+18)/265))); double width=Math.floor((available-(columns-1)*18)/columns); cards.setPrefWrapLength(available); for(Node n:cards.getChildren()) if(n instanceof Region r){r.setMinWidth(width);r.setPrefWidth(width);r.setMaxWidth(width);} }
}
