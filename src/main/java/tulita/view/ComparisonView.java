package tulita.view;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import tulita.model.*;
import tulita.service.ComparisonService;
import tulita.util.*;
import java.util.*;
import java.util.function.*;
import static tulita.view.DashboardView.label;

public final class ComparisonView {
    private ComparisonView() { }
    public static Dialog<Void> show(Window owner, ProductDetails p, List<Listing> listings,
            Consumer<String> openUrl, boolean saved, Runnable toggleWatch) {
        ComparisonService service = new ComparisonService();
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Tulita · Compare " + p.getName()); dialog.initOwner(owner);
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane(); pane.getStyleClass().add("comparison-dialog"); pane.setId("comparison-dialog");
        pane.getStylesheets().add(Objects.requireNonNull(ComparisonView.class.getResource("/styles/tulita.css")).toExternalForm());
        pane.getButtonTypes().add(ButtonType.CLOSE);
        VBox content = new VBox(18); content.setPadding(new Insets(10)); content.setPrefWidth(1050);
        HBox header = new HBox(20); header.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(7,label("THE SAME PRODUCT. A CLEARER CHOICE.","eyebrow"),label(p.getName(),"dialog-title"),label(p.getVariant()+"  ·  "+p.getCategory(),"muted"));
        Region spacer = new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
        Button watch = new Button(saved ? "♥  Saved" : "♡  Save product"); watch.getStyleClass().add("secondary-button");
        watch.setOnAction(e -> { toggleWatch.run(); watch.setText(watch.getText().startsWith("♥") ? "♡  Save product" : "♥  Saved"); });
        header.getChildren().addAll(new ProductArt(p.getArt(),155,100),info,spacer,watch);
        boolean incomplete = service.hasUnknownAvailableTotal(listings);
        String lowest = service.lowestKnownTotal(listings).map(Money::inr).orElse("No eligible offer");
        Label winner = label((incomplete ? "Best known total: " : "Best Price: ") + lowest
            + (incomplete ? "  ·  Delivery is unknown for another available listing." : "  ·  Includes the listed delivery charge."),"best-banner");
        winner.setWrapText(true); winner.setMaxWidth(Double.MAX_VALUE); winner.setId("best-banner");
        TableView<Listing> table = new TableView<>(FXCollections.observableArrayList(service.sorted(listings)));
        table.setId("comparison-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(85); table.setPrefHeight(85*Math.max(1,Math.min(4,listings.size()))+40);
        table.setPlaceholder(new Label("No safely comparable listings for this variant."));
        TableColumn<Listing,String> store = column("Store", l -> l.getStore(),110);
        store.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null); setGraphic(null);
                if (!empty && getIndex()<getTableView().getItems().size()) {
                    Listing l = getTableView().getItems().get(getIndex());
                    VBox box = new VBox(7,label(value,"store-name"));
                    if (service.isBest(l,listings)) box.getChildren().add(label(incomplete ? "Best known" : "Best Price","best-badge"));
                    box.getChildren().add(label(l.isAvailable() ? "In stock" : "Out of stock",l.isAvailable() ? "stock-label" : "unavailable"));
                    setGraphic(box);
                }
            }
        });
        TableColumn<Listing,String> title = column("Store listing", Listing::getTitle,205);
        title.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value,empty); setText(null); setGraphic(null);
                if (!empty) { Label text=label(value,"table-title"); text.setWrapText(true); text.setMaxWidth(195); setGraphic(text); }
            }
        });
        TableColumn<Listing,String> price = column("Price / MRP", l -> Money.inr(l.getPrice())+"\nMRP "+Money.inr(l.getMrp()),115);
        TableColumn<Listing,String> discount = column("Discount", l -> l.getDiscount().toPlainString()+"%",75);
        TableColumn<Listing,String> rating = column("Rating", l -> "★ "+l.getRating(),65);
        TableColumn<Listing,String> delivery = column("Delivery", l -> (l.getDelivery()==null ? "Unknown" : l.getDelivery().signum()==0 ? "Free" : Money.inr(l.getDelivery()))+"\n"+Objects.toString(l.getEstimate(),""),100);
        TableColumn<Listing,String> total = column("Final total", l -> l.finalPrice().map(Money::inr).orElse("Unknown"),110);
        total.getStyleClass().add("total-column");
        TableColumn<Listing,String> action = column("Store link",Listing::getUrl,120);
        action.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value,empty); setText(null); setGraphic(null);
                if (!empty && getIndex()<getTableView().getItems().size()) {
                    Listing l = getTableView().getItems().get(getIndex());
                    Button link = new Button("Search store ↗"); link.getStyleClass().add("store-link");
                    boolean valid = StoreLinks.isAllowed(l.getStore(),value); link.setDisable(!valid);
                    link.setTooltip(new Tooltip(valid ? "Opens a sample store search, not a verified product offer." : "No valid HTTPS store URL provided."));
                    link.setOnAction(e -> {
                        Alert notice = new Alert(Alert.AlertType.CONFIRMATION,
                            "This opens a public store search in your browser. The sample price and availability are not live, verified, or guaranteed. Continue?",ButtonType.OK,ButtonType.CANCEL);
                        notice.initOwner(pane.getScene().getWindow()); notice.setHeaderText("Leaving the demonstration catalog");
                        if (notice.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) {
                            try { openUrl.accept(value); }
                            catch (RuntimeException ex) { Alert error = new Alert(Alert.AlertType.ERROR,"Could not open your browser. Check your operating system’s default browser."); error.initOwner(pane.getScene().getWindow()); error.showAndWait(); }
                        }
                    }); setGraphic(link);
                }
            }
        });
        table.getColumns().add(store); table.getColumns().add(title); table.getColumns().add(price);
        table.getColumns().add(discount); table.getColumns().add(rating); table.getColumns().add(delivery); table.getColumns().add(total); table.getColumns().add(action);
        table.getColumns().forEach(c -> c.setSortable(false));
        table.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(Listing item, boolean empty) {
                super.updateItem(item,empty); getStyleClass().removeAll("best-row","out-row");
                if (!empty && item!=null) {
                    if (service.isBest(item,listings)) getStyleClass().add("best-row");
                    if (!item.isAvailable()) getStyleClass().add("out-row");
                }
            }
        });
        Label explanation = label("How we compare", "note-title");
        Label note = label("Only the same model and exact variant are compared. Best Price means the lowest available price + known delivery charge; tied offers are both highlighted. Item discounts are calculated from MRP, not from the delivered total.","muted"); note.setWrapText(true); note.setPrefWidth(990); note.setMinHeight(40); note.setMaxWidth(Double.MAX_VALUE);
        Label disclaimer = label("SAMPLE DATA ONLY · Illustrations are placeholders. Store links open searches, not verified listings. No scraping or live APIs.","footer"); disclaimer.setWrapText(true);
        content.getChildren().addAll(header,winner,table,explanation,note,disclaimer); pane.setContent(content);
        dialog.show(); return dialog;
    }
    private static TableColumn<Listing,String> column(String name, Function<Listing,String> value, double width) {
        TableColumn<Listing,String> column = new TableColumn<>(name);
        column.setCellValueFactory(d -> new ReadOnlyStringWrapper(value.apply(d.getValue())));
        column.setPrefWidth(width); column.setMinWidth(width-15); return column;
    }
}
