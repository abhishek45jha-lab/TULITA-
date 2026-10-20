package tulita.view;

import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tulita.model.*;
import tulita.service.*;
import tulita.util.Money;
import java.util.*;
import java.util.function.Consumer;

public final class DashboardView extends BorderPane {
    static {
        javafx.scene.text.Font.loadFont(DashboardView.class.getResourceAsStream("/fonts/NotoSansDevanagari-Regular.ttf"),12);
        javafx.scene.text.Font.loadFont(DashboardView.class.getResourceAsStream("/fonts/NotoSansDevanagari-Bold.ttf"),12);
    }
    private final TextField search = new TextField();
    private final ComboBox<String> sort = new ComboBox<>();
    private final FlowPane cards = new FlowPane(18,18);
    private final VBox results = new VBox(18);
    private final Label count = new Label("Loading catalog…");
    private final Label mode = new Label("STARTING");
    private final Label catalogStats = new Label("20 sample products · 3 sample stores");
    private final Label status = new Label();
    private final Label sessionCount = new Label("0");
    private final Button allNav = new Button("◈   Discover");
    private final Button watchNav = new Button("♡   Watchlist");
    private final Map<String, Button> categories = new LinkedHashMap<>();
    private final Button clear = new Button("Clear filters");
    private final Button retry = new Button("Retry connection");
    private final Button searchButton = new Button("Search   →");
    private final ScrollPane scroll;
    private String category = "All products";
    private boolean watchOnly;
    private Runnable onFilter = () -> { };
    private Consumer<ProductDetails> onCompare = p -> { }, onWatch = p -> { };
    private Runnable onRetry = () -> { };
    private boolean busy = true;

    public DashboardView() {
        getStyleClass().add("app");
        setLeft(sidebar());
        VBox main = new VBox(22);
        main.setPadding(new Insets(24,30,24,30));
        HBox topline = new HBox(12);
        Label crumb = label("Discover  /  Compare & save", "muted");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        mode.getStyleClass().add("mode-pill"); mode.setId("mode-label");
        topline.getChildren().addAll(crumb,spacer,mode);
        main.getChildren().addAll(topline,hero(),searchbar(),resultsHeader());
        cards.setId("product-grid");
        status.getStyleClass().add("status"); status.setWrapText(true);
        clear.getStyleClass().add("secondary-button"); clear.setOnAction(e -> reset());
        retry.getStyleClass().add("primary-button"); retry.setOnAction(e -> onRetry.run()); retry.setId("retry");
        results.getChildren().add(cards); main.getChildren().add(results);
        Label foot = label("DEMONSTRATION CATALOG   •   Prices, ratings and availability are fictional. No live store integrations.", "footer");
        foot.setWrapText(true); main.getChildren().add(foot);
        scroll = new ScrollPane(main); scroll.setFitToWidth(true); scroll.getStyleClass().add("main-scroll");
        scroll.viewportBoundsProperty().addListener((obs, old, bounds) -> resizeCards(bounds.getWidth() - 60));
        setCenter(scroll);
        sort.getItems().addAll("Lowest total price", "Highest discount", "Top rated"); sort.getSelectionModel().select(0);
        sort.setId("sort"); sort.setOnAction(e -> filter());
        showLoading();
    }
    private Node sidebar() {
        VBox side = new VBox(10); side.getStyleClass().add("sidebar"); side.setPrefWidth(208); side.setMinWidth(190);
        HBox brand = new HBox(9,label("t.","brand-mark"),label("tulita", "brand")); brand.setAlignment(Pos.CENTER_LEFT);
        Label tagline = label("Compare smart. Shop better.", "side-tagline");
        allNav.getStyleClass().addAll("nav-button", "nav-active"); allNav.setId("discover");
        watchNav.getStyleClass().add("nav-button"); watchNav.setId("watchlist");
        allNav.setOnAction(e -> { watchOnly=false; navigation(); filter(); });
        watchNav.setOnAction(e -> { watchOnly=true; navigation(); filter(); });
        HBox saved = new HBox(6,watchNav,sessionCount); saved.setAlignment(Pos.CENTER_LEFT);
        sessionCount.getStyleClass().add("count-pill");
        Label browse = label("BROWSE CATEGORIES", "side-heading"); VBox.setMargin(browse,new Insets(25,0,8,0));
        side.getChildren().addAll(brand,tagline,new Separator(),allNav,saved,browse);
        for (String c : List.of("All products","Electronics","Footwear","Clothing","Accessories")) {
            Button button = new Button(c); button.setMaxWidth(Double.MAX_VALUE); button.getStyleClass().add("category-button");
            if (c.equals(category)) button.getStyleClass().add("category-active");
            button.setId("category-" + c.toLowerCase(Locale.ROOT).replace(' ','-'));
            button.setOnAction(e -> { category=c; updateCategories(); filter(); });
            categories.put(c,button); side.getChildren().add(button);
        }
        Region fill = new Region(); VBox.setVgrow(fill,Priority.ALWAYS);
        VBox note = new VBox(8,label("A smarter shortlist.","note-title"),label("Save a few favourites.\nCompare before you decide.","muted"));
        note.getStyleClass().add("sidebar-note");
        ((Label)note.getChildren().get(1)).setWrapText(true);
        Label session = label("Watchlist is session-only.\nIt resets when you close Tulita.","side-tagline"); session.setWrapText(true);
        side.getChildren().addAll(fill,note,session); return side;
    }
    private Node hero() {
        HBox hero = new HBox(22); hero.setAlignment(Pos.CENTER_LEFT); hero.getStyleClass().add("hero");
        VBox text = new VBox(8);
        Label kicker = label("LESS GUESSWORK. MORE GOOD FINDS.","hero-kicker");
        Label heading = label("Good finds. Better prices.","hero-title");
        Label subtitle = label("Compare Smart. Shop Better. Save More.","hero-subtitle");
        text.getChildren().addAll(kicker,heading,subtitle);
        Region space = new Region(); HBox.setHgrow(space,Priority.ALWAYS);
        VBox value = new VBox(4,label("₹", "hero-rupee"),label("One search. More perspective.","hero-small")); value.setAlignment(Pos.CENTER);
        hero.getChildren().addAll(text,space,value);
        value.setMinWidth(170); return hero;
    }
    private Node searchbar() {
        HBox box = new HBox(12); box.setAlignment(Pos.CENTER_LEFT);
        search.setPromptText("Try ‘Sony’, ‘Pegasus 40’ or ‘backpack’"); search.setId("search");
        search.getStyleClass().add("search-field"); HBox.setHgrow(search,Priority.ALWAYS);
        searchButton.getStyleClass().add("primary-button"); searchButton.setId("search-button");
        search.setOnAction(e -> filter()); searchButton.setOnAction(e -> filter());
        // In-memory filtering remains immediate; DB loading happens on a worker.
        search.textProperty().addListener((o,a,b) -> filter());
        box.getChildren().addAll(search,searchButton); return box;
    }
    private Node resultsHeader() {
        VBox block = new VBox(9);
        HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT);
        count.getStyleClass().add("section-title"); count.setId("result-count");
        Region space = new Region(); HBox.setHgrow(space,Priority.ALWAYS);
        row.getChildren().addAll(count,space,label("Sort by", "muted"),sort);
        catalogStats.getStyleClass().add("muted");
        block.getChildren().addAll(row,catalogStats); return block;
    }
    public void actions(Runnable filter, Consumer<ProductDetails> compare, Consumer<ProductDetails> watch, Runnable retryAction) {
        onFilter=filter; onCompare=compare; onWatch=watch; onRetry=retryAction;
    }
    private void filter() { if (!busy) onFilter.run(); }
    public String query() { return search.getText(); }
    public String category() { return category; }
    public boolean watchOnly() { return watchOnly; }
    public CatalogService.Sort sortOrder() { return CatalogService.Sort.values()[Math.max(0,sort.getSelectionModel().getSelectedIndex())]; }
    public void setMode(String value) { mode.setText(value); }
    private void navigation() {
        allNav.getStyleClass().remove("nav-active"); watchNav.getStyleClass().remove("nav-active");
        (watchOnly ? watchNav : allNav).getStyleClass().add("nav-active");
    }
    private void updateCategories() {
        categories.forEach((name,button) -> { button.getStyleClass().remove("category-active"); if (name.equals(category)) button.getStyleClass().add("category-active"); });
    }
    private void reset() {
        search.clear(); category="All products"; sort.getSelectionModel().select(0); updateCategories(); filter();
    }
    private void disableFilters(boolean value) {
        search.setDisable(value); searchButton.setDisable(value); sort.setDisable(value);
        allNav.setDisable(value); watchNav.setDisable(value); categories.values().forEach(b -> b.setDisable(value));
    }
    public void showLoading() {
        busy=true; disableFilters(true); count.setText("Loading catalog…");
        ProgressIndicator progress = new ProgressIndicator(); progress.setMaxSize(35,35);
        status.setText("Loading your comparison catalog…");
        results.getChildren().setAll(state(progress,status));
    }
    public void showError(String message) {
        busy=true; disableFilters(true); count.setText("Let’s get you connected");
        status.setText(message); results.getChildren().setAll(state(label("!", "state-icon"),status,retry));
    }
    public void showProducts(List<ProductDetails> products, CatalogService catalog, WatchlistService watchlist) {
        busy=false; disableFilters(false);
        sessionCount.setText(String.valueOf(watchlist.size()));
        count.setText((watchOnly ? "Your watchlist" : "Explore the catalog") + "  ·  " + products.size());
        catalogStats.setText(catalog.productCount()+" products · "+catalog.listingCount()+" comparable sample listings · Card sorting uses the best known-total offer"
            + (catalog.excludedCount()>0 ? " · "+catalog.excludedCount()+" unsafe matches excluded" : ""));
        cards.getChildren().clear();
        if (products.isEmpty()) {
            status.setText(watchOnly && watchlist.size()==0 ? "Your shortlist starts here. Save a product with the ♡ button." : "No products found. Try a shorter name or a different category.");
            results.getChildren().setAll(state(label("⌕", "state-icon"),status,clear));
        } else {
            products.forEach(p -> cards.getChildren().add(card(p,catalog,watchlist)));
            results.getChildren().setAll(cards); resizeCards(scroll.getViewportBounds().getWidth()-60);
        }
    }
    private VBox state(Node... content) {
        VBox box = new VBox(16, content); box.setAlignment(Pos.CENTER); box.setPadding(new Insets(45)); box.setMinHeight(230); box.getStyleClass().add("state-panel"); return box;
    }
    private VBox card(ProductDetails p, CatalogService catalog, WatchlistService watchlist) {
        VBox card = new VBox(10); card.getStyleClass().add("product-card"); card.setId("product-"+p.getId());
        StackPane image = new StackPane(new ProductArt(p.getArt(),260,150)); image.getStyleClass().add("product-image");
        Button watch = new Button(watchlist.contains(p.getId()) ? "♥" : "♡"); watch.setId("watch-"+p.getId()); watch.getStyleClass().add("heart-button");
        watch.setTooltip(new Tooltip("Add/remove from session watchlist")); watch.setAccessibleText("Save or remove " + p.getName());
        watch.setOnAction(e -> onWatch.accept(p)); StackPane.setAlignment(watch,Pos.TOP_RIGHT); StackPane.setMargin(watch,new Insets(7)); image.getChildren().add(watch);
        VBox body = new VBox(8); body.setPadding(new Insets(2,15,15,15));
        Label cat = label(p.getCategory().toUpperCase(Locale.ROOT),"card-category");
        Label name = label(p.getName(),"product-name"); name.setWrapText(true); name.setMinHeight(40);
        Label variant = label(p.getVariant(),"muted");
        Optional<Listing> winner = catalog.best(p);
        HBox priceLine = new HBox(7); priceLine.setAlignment(Pos.CENTER_LEFT);
        Label total = label(winner.flatMap(Listing::finalPrice).map(Money::inr).orElse("No known total"),"price");
        priceLine.getChildren().add(total);
        winner.ifPresent(l -> priceLine.getChildren().add(label(l.getDiscount().toPlainString()+"% off", "discount")));
        String priceCaption = winner.map(l -> "Delivered total at " + l.getStore() + "  ·  ★ " + l.getRating()).orElse("No available priced listing");
        Label caption = label(priceCaption,"card-caption"); caption.setWrapText(true);
        if (new ComparisonService().hasUnknownAvailableTotal(catalog.listings(p))) caption.setText(priceCaption+" · Other delivery unknown");
        Button compare = new Button("Compare "+catalog.listings(p).size()+" stores    →"); compare.setId("compare-"+p.getId());
        compare.getStyleClass().add("compare-button"); compare.setMaxWidth(Double.MAX_VALUE); compare.setOnAction(e -> onCompare.accept(p));
        body.getChildren().addAll(cat,name,variant,priceLine,caption,compare); card.getChildren().addAll(image,body); return card;
    }
    private void resizeCards(double available) {
        if (available<=0) return;
        int columns = Math.max(1,Math.min(4,(int)((available+18)/265)));
        double width = Math.floor((available - (columns-1)*18)/columns);
        for (Node node : cards.getChildren()) if (node instanceof Region region) { region.setPrefWidth(width); region.setMinWidth(width); region.setMaxWidth(width); }
        cards.setPrefWrapLength(available);
    }
    public static Label label(String text, String style) { Label l = new Label(text); l.getStyleClass().add(style); if(text!=null&&text.codePoints().anyMatch(c->c>=0x0900&&c<=0x097f))l.setStyle("-fx-font-family: 'Noto Sans Devanagari';"); return l; }
}
