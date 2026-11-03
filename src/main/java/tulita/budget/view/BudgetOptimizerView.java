package tulita.budget.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleGroup;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tulita.budget.model.BudgetRecommendation;
import tulita.budget.model.LineCharges;
import tulita.budget.model.RecommendationDelta;
import tulita.budget.model.WhatIfOption;
import tulita.budget.model.BudgetRequest;
import tulita.budget.model.OptimizationResult;
import tulita.budget.model.ProductScore;
import tulita.budget.model.RecommendationAlternative;
import tulita.budget.model.RecommendationMode;
import tulita.budget.model.ScoringCriterion;
import tulita.budget.model.SelectedProduct;
import tulita.budget.model.SavedBudgetRun;
import tulita.budget.service.BudgetMoney;
import tulita.budget.service.CurrencyConverter;
import tulita.budget.service.CategorySpecProfiles;
import tulita.photocatalog.model.PhotoProduct;
import tulita.photocatalog.service.CatalogValidator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static tulita.view.DashboardView.label;

/**
 * The Budget Optimizer workspace: budget, categories, preferences, and the recommendation it produces.
 *
 * <p>The view only reads input and renders results; it never optimises, never touches the catalogue and
 * never blocks. {@link tulita.budget.controller.BudgetOptimizerController} owns that work.</p>
 */
public final class BudgetOptimizerView extends BorderPane {

    /** One editable row of step 2. */
    private final class CategoryRow {
        final String category;
        final CheckBox required = new CheckBox();
        final Label name;
        final Spinner<Integer> min = new Spinner<>();
        final Spinner<Integer> max = new Spinner<>();
        final ComboBox<String> brand = new ComboBox<>();
        final ComboBox<String> priority = new ComboBox<>();
        final ComboBox<String> minimumSpec = new ComboBox<>();
        final TextField minimumSpecValue = new TextField();
        final TextField target = new TextField();

        CategoryRow(String category, int productCount) {
            this.category = category;
            name = label(category + " (" + productCount + ")", "card-category");
            name.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            name.setTooltip(new Tooltip(category + " — " + productCount + " product"
                + (productCount == 1 ? "" : "s") + " in the catalogue"));
            required.setId("budget-required-" + slug(category));
            min.setId("budget-min-" + slug(category));
            max.setId("budget-max-" + slug(category));
            brand.setId("budget-brand-" + slug(category));
            priority.setId("budget-priority-" + slug(category));
            minimumSpec.setId("budget-spec-" + slug(category));
            minimumSpecValue.setId("budget-spec-value-" + slug(category));
            target.setId("budget-target-" + slug(category));
            min.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1, 0));
            max.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99, 1));
            min.setEditable(true);
            max.setEditable(true);
            min.setPrefWidth(72);
            max.setPrefWidth(72);
            brand.setPrefWidth(120);
            priority.getItems().setAll(List.of("Low", "Medium", "High"));
            priority.getSelectionModel().select("Medium");
            priority.setPrefWidth(110);
            minimumSpec.setPrefWidth(156);
            minimumSpecValue.setPromptText("min");
            minimumSpecValue.setPrefWidth(64);
            target.setPromptText("optional");
            target.setPrefWidth(82);
            minimumSpec.getItems().setAll(CategorySpecProfiles.forCategory(category).labels());
            minimumSpec.getSelectionModel().selectFirst();

            required.setOnAction(e -> {
                boolean on = required.isSelected();
                min.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    0, 99, on ? Math.max(1, min.getValue()) : 0));
                if (max.getValue() < min.getValue()) {
                    max.getValueFactory().setValue(min.getValue());
                }
                priority.getSelectionModel().select(on ? "High" : "Low");
            });
        }

        BudgetRequest.CategoryRequirement toRequirement() {
            String specMetric = minimumSpec.getValue();
            String specText = minimumSpecValue.getText() == null ? "" : minimumSpecValue.getText().trim();
            BudgetRequest.MinimumSpec minimum = null;
            if (!specText.isEmpty()) {
                double value;
                try {
                    value = Double.parseDouble(specText);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("The minimum specification for " + category
                        + " must be a number.");
                }
                if (value <= 0) {
                    throw new IllegalArgumentException("The minimum specification for " + category
                        + " must be greater than zero.");
                }
                if (specMetric == null) {
                    throw new IllegalArgumentException("Choose which specification to compare for " + category + ".");
                }
                minimum = new BudgetRequest.MinimumSpec(specMetric, value);
            }
            BigDecimal targetPrice = null;
            String targetText = target.getText() == null ? "" : target.getText().trim();
            if (!targetText.isEmpty()) {
                targetPrice = BudgetMoney.of(targetText);
                if (targetPrice.signum() <= 0) {
                    throw new IllegalArgumentException("The target price for " + category
                        + " must be greater than zero.");
                }
            }
            String preferred = brand.getValue();
            boolean any = preferred == null || preferred.startsWith("Any brand");
            int minQty = min.getValue() == null ? 0 : min.getValue();
            int maxQty = max.getValue() == null ? 1 : max.getValue();
            boolean isRequired = required.isSelected();
            if (isRequired && minQty < 1) {
                minQty = 1;
            }
            if (maxQty < minQty) {
                maxQty = minQty;
            }
            return new BudgetRequest.CategoryRequirement(category, isRequired, minQty, maxQty,
                any ? null : preferred, BudgetRequest.Priority.of(priority.getValue()), targetPrice, minimum);
        }
    }

    private final VBox categoryBox = new VBox(6);
    private final VBox resultsBox = new VBox(18);
    private final VBox savedBox = new VBox(6);

    private final List<CategoryRow> rows = new ArrayList<>();
    private final Map<String, TextField> deliveryFields = new LinkedHashMap<>();

    private final TextField budgetField = new TextField();
    private final Label budgetCurrency = label("", "muted");
    private final Label validation = label("", "kpi-warn");
    private final Label status = label("Catalogue not loaded yet.", "muted");
    private final Label catalogueSummary = label("", "muted");

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final Map<RecommendationMode, RadioButton> modeButtons = new LinkedHashMap<>();
    private final Map<ScoringCriterion, Slider> weightSliders = new LinkedHashMap<>();
    private final Map<ScoringCriterion, Label> weightValues = new LinkedHashMap<>();
    private final Label weightSummary = label("", "muted");

    private final TextField discountPercent = new TextField("0");
    private final TextField discountMinimum = new TextField("0");

    private final Button optimize = new Button("Optimize My Budget");
    private final Button save = new Button("Save recommendation");
    private final Button export = new Button("Export CSV");
    private final Button print = new Button("Print / save as PDF");
    private final Button reset = new Button("Reset");

    private Runnable onOptimize = () -> { };
    private Runnable onSave = () -> { };
    private Runnable onExport = () -> { };
    private Runnable onReset = () -> { };

    private String currency = "USD";
    private CurrencyConverter converter = CurrencyConverter.catalogueOnly("USD");
    private final ComboBox<String> currencyChoice = new ComboBox<>();
    private final TextField inrRate = new TextField();
    private final Label conversionNote = new Label();
    private final Label ratePrefix = label("", "muted");
    private final HBox quickAmounts = new HBox(8);
    /** Exactly what the shopper typed, in the currency on screen (no round-trip through a rate). */
    private BigDecimal typedBudget;
    private Path catalogueRoot;
    private BudgetRecommendation rendered;
    private BudgetRequest renderedRequest;
    private Map<String, LineCharges> renderedCharges = Map.of();
    private Consumer<String> opener = url -> { };
    private Consumer<WhatIfOption> onWhatIf = option -> { };
    private final VBox exploreBox = new VBox(10);

    public BudgetOptimizerView() {
        getStyleClass().add("app");
        VBox main = new VBox(18);
        main.getStyleClass().add("workspace");
        main.setPadding(new Insets(24, 32, 30, 32));
        main.getChildren().addAll(header(), stepBudget(), stepCategories(), stepPreferences(), stepCharges(),
            actionBar(), resultsBox, savedCard(), footer());
        ScrollPane scroll = new ScrollPane(main);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("main-scroll");
        setCenter(scroll);
        setBottom(null);
        busy(true);
    }

    private Node header() {
        Label kicker = label("PLANNER · BUDGET-CONSTRAINED RECOMMENDATIONS", "hero-kicker");
        Label title = label("What can I buy with this budget?", "hero-title");
        Label subtitle = label("Tell Tulita what you need, and it searches the curated catalogue for the best"
            + " combination inside your limit.", "hero-copy");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(760);
        VBox text = new VBox(8, kicker, title, subtitle);
        catalogueSummary.setWrapText(true);
        VBox box = new VBox(10, text, catalogueSummary);
        box.getStyleClass().add("hero");
        return box;
    }

    private Node stepBudget() {
        budgetField.setId("budget-amount");
        budgetField.setPromptText("e.g. 5000");
        budgetField.setPrefWidth(220);
        budgetField.getStyleClass().add("search-field");
        budgetField.textProperty().addListener((o, a, b) -> validateBudgetQuietly());
        budgetCurrency.setText("in the catalogue currency");
        quickAmounts.setId("budget-quick");
        fillQuickAmounts(false);
        validation.setWrapText(true);

        currencyChoice.setId("budget-currency");
        currencyChoice.getItems().setAll("catalogue currency", "INR (\u20b9)");
        currencyChoice.getSelectionModel().selectFirst();
        currencyChoice.setPrefWidth(180);
        inrRate.setId("budget-inr-rate");
        inrRate.setPromptText("e.g. 83.50");
        inrRate.setPrefWidth(120);
        inrRate.getStyleClass().add("search-field");
        inrRate.setDisable(true);
        ratePrefix.setText("1 " + currency + " =");
        ratePrefix.setId("budget-rate-prefix");
        conversionNote.setId("budget-conversion-note");
        conversionNote.getStyleClass().add("chart-note");
        conversionNote.setWrapText(true);
        conversionNote.setMaxWidth(820);

        currencyChoice.valueProperty().addListener((o, a, b) -> {
            boolean rupees = b != null && b.startsWith("INR");
            inrRate.setDisable(!rupees);
            budgetCurrency.setText(rupees ? "in \u20b9 (INR)" : "in " + currency + " (catalogue currency)");
            fillQuickAmounts(rupees);
            refreshConverter();
        });
        inrRate.textProperty().addListener((o, a, b) -> refreshConverter());

        HBox currencyLine = new HBox(10, label("Show amounts in", "muted"), currencyChoice, ratePrefix,
            inrRate, label("\u20b9", "muted"));
        currencyLine.setAlignment(Pos.CENTER_LEFT);

        HBox line = new HBox(12, budgetField, budgetCurrency, quickAmounts);
        line.setAlignment(Pos.CENTER_LEFT);
        return card("STEP 1 · ENTER BUDGET",
            "Type the budget you want to plan with. You can plan in \u20b9 (INR): the catalogue records prices"
                + " in " + currency + ", and Tulita never invents an exchange rate, so you supply the rate you"
                + " want and every converted amount is labelled with it. The optimiser itself always works in"
                + " the recorded " + currency + " amounts, so conversion cannot change which basket wins or"
                + " let a basket exceed the budget.",
            new VBox(10, currencyLine, line, conversionNote, validation));
    }

    /** Quick-fill chips sized for the currency on screen, so they are useful in either mode. */
    private void fillQuickAmounts(boolean rupees) {
        quickAmounts.getChildren().clear();
        List<String> amounts = rupees
            ? List.of("25000", "50000", "100000", "200000")
            : List.of("500", "1000", "2500", "5000");
        for (String amount : amounts) {
            Button chip = new Button(amount);
            chip.setId("budget-quick-" + amount);
            chip.getStyleClass().add("ghost-button");
            chip.setOnAction(e -> budgetField.setText(amount));
            quickAmounts.getChildren().add(chip);
        }
    }

    /** Re-reads the currency controls; a blank/invalid rate keeps the catalogue currency and says why. */
    private void refreshConverter() {
        String choice = currencyChoice.getValue();
        if (choice == null || !choice.startsWith("INR")) {
            converter = CurrencyConverter.catalogueOnly(currency);
            conversionNote.setText("Showing the catalogue's own amounts: " + converter.describe());
            validateBudgetQuietly();
            return;
        }
        try {
            converter = CurrencyConverter.userDeclared(currency, "INR", inrRate.getText());
            conversionNote.setText(converter.describe());
        } catch (IllegalArgumentException e) {
            converter = CurrencyConverter.catalogueOnly(currency);
            conversionNote.setText(e.getMessage() + " Amounts therefore stay in " + currency + ".");
        }
        validateBudgetQuietly();
    }

    /**
     * The budget as the shopper should read it: the amount they typed, plus the catalogue-currency
     * figure the optimiser actually used when a conversion is active.
     */
    public String describeBudget(BudgetRequest request) {
        if (!converter.converts()) {
            return BudgetMoney.format(request.budget(), request.currency());
        }
        return converter.format(request.budget()) + " (the catalogue's "
            + BudgetMoney.format(request.budget(), request.currency()) + " at your rate)";
    }

    /** The active converter (catalogue currency unless the shopper typed a rate). */
    public CurrencyConverter converter() {
        return converter;
    }

    /** A catalogue-currency amount rendered in the shopper's chosen currency. */
    private String money(BigDecimal catalogueAmount) {
        return converter.format(catalogueAmount);
    }

    private Node stepCategories() {
        categoryBox.setPadding(new Insets(2, 0, 0, 0));
        return card("STEP 2 · SELECT PRODUCT CATEGORIES",
            "Tick a category to make it mandatory, then set quantities, brand, priority, a minimum"
                + " specification or a target price. Unticked categories are optional: the planner only adds"
                + " them when they still fit the budget. The catalogue's largest category is pre-ticked so the"
                + " form is usable immediately — change it freely.",
            categoryBox);
    }

    private Node stepPreferences() {
        GridPane modes = new GridPane();
        modes.setHgap(18);
        modes.setVgap(6);
        int column = 0;
        for (RecommendationMode mode : RecommendationMode.values()) {
            RadioButton button = new RadioButton(mode.label());
            button.setToggleGroup(modeGroup);
            button.setUserData(mode);
            button.setId("budget-mode-" + mode.name().toLowerCase(Locale.ROOT));
            button.setWrapText(true);
            button.setMaxWidth(330);
            modeButtons.put(mode, button);
            Label help = label(mode.description(), "muted");
            help.setWrapText(true);
            help.setMaxWidth(330);
            VBox block = new VBox(2, button, help);
            modes.add(block, column % 2, column / 2);
            column++;
        }
        modeButtons.get(RecommendationMode.BEST_VALUE).setSelected(true);

        VBox sliders = new VBox(8);
        for (ScoringCriterion criterion : ScoringCriterion.values()) {
            Slider slider = new Slider(0, 100, switch (criterion) {
                case PRICE, SPECIFICATIONS -> 30;
                case VALUE -> 20;
                default -> 10;
            });
            slider.setPrefWidth(320);
            slider.setId("budget-weight-" + criterion.name().toLowerCase(Locale.ROOT));
            Label value = label("", "muted");
            value.setMinWidth(52);
            Label name = label(criterion.label(), "card-category");
            name.setMinWidth(150);
            Label hint = label(criterion.description(), "muted");
            hint.setWrapText(true);
            hint.setMaxWidth(430);
            slider.valueProperty().addListener((o, a, b) -> {
                value.setText(Math.round(b.doubleValue()) + " %");
                updateWeightSummary();
            });
            weightSliders.put(criterion, slider);
            weightValues.put(criterion, value);
            value.setText(Math.round(slider.getValue()) + " %");
            HBox row = new HBox(12, name, slider, value);
            row.setAlignment(Pos.CENTER_LEFT);
            sliders.getChildren().addAll(row, hint);
        }
        weightSummary.setWrapText(true);
        updateWeightSummary();

        VBox content = new VBox(16, new VBox(10, label("RECOMMENDATION MODE", "side-heading"), modes),
            new VBox(10, label("PREFERENCE WEIGHTS", "side-heading"), sliders, weightSummary));
        return card("STEP 3 · SET PREFERENCES",
            "Weights are normalised, so they do not have to add up to 100."
                + " A criterion the catalogue cannot support is dropped and disclosed instead of guessed.",
            content);
    }

    private Node stepCharges() {
        deliveryFields.clear();
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        int row = 0;
        for (String store : List.of("Amazon", "Walmart", "Target", "Sears")) {
            TextField field = new TextField("0");
            field.setId("budget-delivery-" + store.toLowerCase(Locale.ROOT));
            field.setPrefWidth(110);
            deliveryFields.put(store, field);
            grid.add(label(store, "card-category"), 0, row);
            grid.add(field, 1, row);
            row++;
        }
        discountPercent.setId("budget-discount-percent");
        discountMinimum.setId("budget-discount-minimum");
        discountPercent.setPrefWidth(90);
        discountMinimum.setPrefWidth(120);
        VBox left = new VBox(8, label("DELIVERY PER RETAILER (CHARGED ONCE PER SHOP)", "side-heading"), grid);
        VBox right = new VBox(8, label("DISCOUNT", "side-heading"),
            new HBox(10, label("percent", "card-category"), discountPercent),
            new HBox(10, label("minimum item subtotal", "card-category"), discountMinimum));
        HBox content = new HBox(40, left, right);
        return card("STEP 4 · OPTIONAL CHARGES",
            "The catalogue records no shipping or promotion data, so these are your own assumptions and are"
                + " labelled as estimates everywhere they are used. Leave them at zero to compare item prices only.",
            content);
    }

    private Node actionBar() {
        optimize.setId("budget-optimize");
        optimize.getStyleClass().addAll("primary-button", "compare-button");
        save.setId("budget-save");
        export.setId("budget-export");
        print.setId("budget-print");
        reset.setId("budget-reset");
        save.getStyleClass().add("secondary-button");
        export.getStyleClass().add("secondary-button");
        print.getStyleClass().add("secondary-button");
        reset.getStyleClass().add("ghost-button");
        optimize.setOnAction(e -> onOptimize.run());
        save.setOnAction(e -> onSave.run());
        export.setOnAction(e -> onExport.run());
        print.setOnAction(e -> printPlan());
        reset.setOnAction(e -> onReset.run());
        save.setDisable(true);
        export.setDisable(true);
        print.setDisable(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        status.setWrapText(true);
        HBox buttons = new HBox(10, optimize, save, export, print, reset);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, buttons, status);
        box.getStyleClass().add("toolbar-card");
        return box;
    }

    private Node savedCard() {
        return card("SAVED RECOMMENDATIONS",
            "Stored as JSON next to the downloaded vision model; the catalogue files are never modified.",
            savedBox);
    }

    private Node footer() {
        Label note = label("TULITA plans against recorded, curated observations. It cannot promise current"
            + " availability, live prices or a final payable total, and it never invents a rating, a delivery"
            + " charge or a discount.", "footer");
        note.setWrapText(true);
        return note;
    }

    private VBox card(String heading, String hint, Node content) {
        Label title = label(heading, "eyebrow");
        Label help = label(hint, "muted");
        help.setWrapText(true);
        VBox box = new VBox(10, title, help, new Separator(), content);
        box.getStyleClass().add("toolbar-card");
        return box;
    }

    // ------------------------------------------------------------------ data in
    /** Populates the category rows, brands and retailer fields from the real catalogue. */
    public void loadCatalogue(List<PhotoProduct> products, String catalogueCurrency, Path root) {
        this.currency = catalogueCurrency;
        this.catalogueRoot = root;
        rows.clear();
        categoryBox.getChildren().clear();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, List<String>> brands = new LinkedHashMap<>();
        for (PhotoProduct product : products) {
            counts.merge(product.category(), 1, Integer::sum);
            brands.computeIfAbsent(product.category(), k -> new ArrayList<>());
            if (!brands.get(product.category()).contains(product.brand())) {
                brands.get(product.category()).add(product.brand());
            }
        }
        List<String> categories = new ArrayList<>(counts.keySet());
        categories.sort(String::compareTo);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(7);
        double[] widths = {176, 62, 72, 72, 122, 112, 156, 66, 80};
        double totalWidth = 0;
        for (double width : widths) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPrefWidth(width);
            constraints.setMinWidth(width);
            grid.getColumnConstraints().add(constraints);
            totalWidth += width;
        }
        grid.setPrefWidth(totalWidth);
        String[] headings = {"CATEGORY", "REQUIRED", "MIN", "MAX", "BRAND", "PRIORITY", "MIN SPEC", "VALUE", "TARGET"};
        for (int i = 0; i < headings.length; i++) {
            grid.add(label(headings[i], "side-heading"), i, 0);
        }
        int rowIndex = 1;
        for (String category : categories) {
            CategoryRow row = new CategoryRow(category, counts.get(category));
            row.brand.getItems().add("Any brand");
            List<String> list = brands.get(category);
            list.sort(String::compareToIgnoreCase);
            row.brand.getItems().addAll(list);
            row.brand.getSelectionModel().selectFirst();
            grid.add(row.name, 0, rowIndex);
            grid.add(row.required, 1, rowIndex);
            grid.add(row.min, 2, rowIndex);
            grid.add(row.max, 3, rowIndex);
            grid.add(row.brand, 4, rowIndex);
            grid.add(row.priority, 5, rowIndex);
            grid.add(row.minimumSpec, 6, rowIndex);
            grid.add(row.minimumSpecValue, 7, rowIndex);
            grid.add(row.target, 8, rowIndex);
            rows.add(row);
            rowIndex++;
        }
        categoryBox.getChildren().add(grid);
        applyDefaultSelection();
        ratePrefix.setText("1 " + currency + " =");
        if (currencyChoice.getValue() != null && currencyChoice.getValue().startsWith("INR")) {
            budgetCurrency.setText("in \u20b9 (INR)");
            refreshConverter();
        } else {
            budgetCurrency.setText("in " + currency + " (catalogue currency)");
            converter = CurrencyConverter.catalogueOnly(currency);
            conversionNote.setText("Showing the catalogue's own amounts: " + converter.describe());
        }
        catalogueSummary.setText(products.size() + " products · " + categories.size() + " categories · "
            + "currency " + currency + " · amounts are recorded historical observations, not live prices.");
        status.setText("Catalogue ready. Set a budget and press Optimize My Budget.");
        busy(false);
    }

    /** Ticks the category that holds the most products, so the form is usable on first open. */
    private void applyDefaultSelection() {
        CategoryRow best = null;
        int bestCount = -1;
        for (CategoryRow row : rows) {
            int count = Integer.parseInt(row.name.getText().replaceAll(".*\\((\\d+)\\).*", "$1"));
            if (count > bestCount) {
                bestCount = count;
                best = row;
            }
        }
        if (best != null) {
            best.required.setSelected(true);
            best.min.getValueFactory().setValue(1);
            best.priority.getSelectionModel().select("High");
        }
    }

    public void busy(boolean value) {
        optimize.setDisable(value);
        if (value) {
            status.setText("Optimising…");
        }
    }

    public void status(String text) {
        status.setText(text);
    }

    public void showError(String text) {
        validation.setText(text);
        busy(false);
        save.setDisable(true);
        export.setDisable(true);
        print.setDisable(true);
    }

    public void clearValidation() {
        validation.setText("");
    }

    private void validateBudgetQuietly() {
        String text = budgetField.getText() == null ? "" : budgetField.getText().trim();
        if (text.isEmpty()) {
            validation.setText("");
            return;
        }
        try {
            BigDecimal value = BudgetMoney.of(text);
            validation.setText(value.signum() <= 0
                ? "The budget must be greater than zero." : "");
        } catch (IllegalArgumentException e) {
            validation.setText(e.getMessage());
        }
    }

    private void updateWeightSummary() {
        int total = 0;
        for (Slider slider : weightSliders.values()) {
            total += (int) Math.round(slider.getValue());
        }
        weightSummary.setText(total == 0
            ? "All weights are zero: give at least one criterion a weight."
            : "Weights total " + total + " and are normalised automatically"
            + " (for example 30/30/20/10/10 becomes 30 % / 30 % / 20 % / 10 % / 10 %).");
    }

    // ------------------------------------------------------------------ request out
    /** Reads the form into a request; throws IllegalArgumentException with a readable message. */
    public BudgetRequest readRequest() {
        clearValidation();
        BigDecimal typed = BudgetMoney.of(budgetField.getText());
        if (typed.signum() <= 0) {
            throw new IllegalArgumentException("The budget must be greater than zero.");
        }
        // The typed amount is in the currency on screen; the optimiser only ever sees the
        // catalogue currency. A rate is required whenever those two differ.
        if (currencyChoice.getValue() != null && currencyChoice.getValue().startsWith("INR")
            && (inrRate.getText() == null || inrRate.getText().isBlank())) {
            throw new IllegalArgumentException("Enter how many \u20b9 you want to use for one " + currency
                + ". Tulita does not supply exchange rates, so this is your own assumption.");
        }
        typedBudget = typed;
        BigDecimal budget = converter.toCatalogue(typed);
        if (budget.signum() <= 0) {
            throw new IllegalArgumentException("At your rate of 1 " + currency + " = "
                + inrRate.getText().trim() + " \u20b9, " + converter.formatDisplay(typed)
                + " is less than one " + currency + " cent. Enter a larger budget or a smaller rate.");
        }
        List<BudgetRequest.CategoryRequirement> requirements = new ArrayList<>();
        for (CategoryRow row : rows) {
            if (!row.required.isSelected() && (row.min.getValue() == null || row.min.getValue() == 0)) {
                continue; // untouched optional row
            }
            requirements.add(row.toRequirement());
        }
        if (requirements.isEmpty()) {
            throw new IllegalArgumentException("Tick at least one category as required before optimising.");
        }
        RecommendationMode mode = RecommendationMode.BEST_VALUE;
        for (Map.Entry<RecommendationMode, RadioButton> entry : modeButtons.entrySet()) {
            if (entry.getValue().isSelected()) {
                mode = entry.getKey();
            }
        }
        int price = (int) Math.round(weightSliders.get(ScoringCriterion.PRICE).getValue());
        int specs = (int) Math.round(weightSliders.get(ScoringCriterion.SPECIFICATIONS).getValue());
        int value = (int) Math.round(weightSliders.get(ScoringCriterion.VALUE).getValue());
        int brand = (int) Math.round(weightSliders.get(ScoringCriterion.BRAND).getValue());
        int warranty = (int) Math.round(weightSliders.get(ScoringCriterion.WARRANTY).getValue());
        if (price + specs + value + brand + warranty == 0) {
            throw new IllegalArgumentException("Give at least one preference weight a value above zero.");
        }
        Map<String, BigDecimal> fees = new LinkedHashMap<>();
        for (Map.Entry<String, TextField> entry : deliveryFields.entrySet()) {
            String text = entry.getValue().getText() == null ? "" : entry.getValue().getText().trim();
            if (text.isEmpty()) {
                continue;
            }
            BigDecimal fee = BudgetMoney.of(text);
            if (fee.signum() < 0) {
                throw new IllegalArgumentException("The delivery charge for " + entry.getKey()
                    + " cannot be negative.");
            }
            if (fee.signum() > 0) {
                fees.put(entry.getKey(), fee);
            }
        }
        BigDecimal percent = BudgetMoney.of(discountPercent.getText());
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("The discount percentage must be between 0 and 100.");
        }
        BigDecimal minimum = BudgetMoney.of(discountMinimum.getText());
        if (minimum.signum() < 0) {
            throw new IllegalArgumentException("The discount minimum cannot be negative.");
        }
        BudgetRequest request = new BudgetRequest(budget, currency, requirements,
            new tulita.budget.model.PreferenceWeights(price, specs, value, brand, warranty), mode, fees,
            percent, minimum);
        for (BudgetRequest.CategoryRequirement requirement : requirements) {
            if (!requirement.required() && requirement.minQuantity() > 0) {
                throw new IllegalArgumentException("Optional category " + requirement.category()
                    + " cannot demand a minimum quantity; mark it required instead.");
            }
        }
        return request;
    }

    // ------------------------------------------------------------------ results
    /** Renders an optimisation result. */
    public void showResult(OptimizationResult result, BudgetRequest request) {
        showResult(result, request, null, List.of());
    }

    /** Renders an optimisation result with the change since the previous run and the what-if options. */
    public void showResult(OptimizationResult result, BudgetRequest request, RecommendationDelta delta,
                           List<WhatIfOption> whatIfOptions) {
        resultsBox.getChildren().clear();
        busy(false);
        save.setDisable(result.primary() == null);
        export.setDisable(result.primary() == null);
        print.setDisable(result.primary() == null);
        rendered = result.primary();
        renderedRequest = request;
        renderedCharges = result.primary() == null ? Map.of()
            : new tulita.budget.service.BudgetCalculationService()
                .allocate(request, result.primary().selected());
        if (!result.feasible() || result.primary() == null) {
            resultsBox.getChildren().add(infeasibleCard(result, request));
            resultsBox.getChildren().add(exploreCard(whatIfOptions, null));
            status.setText("No valid combination was found. Nothing was relaxed silently.");
            return;
        }
        BudgetRecommendation recommendation = result.primary();
        if (delta != null) {
            resultsBox.getChildren().add(deltaCard(delta));
        }
        resultsBox.getChildren().add(summaryCard(recommendation, request, result));
        resultsBox.getChildren().add(exploreCard(whatIfOptions, recommendation));
        resultsBox.getChildren().add(chartCard(recommendation, request));
        resultsBox.getChildren().add(selectedCard(recommendation));
        if (!result.alternatives().isEmpty()) {
            resultsBox.getChildren().add(alternativesCard(result));
        }
        resultsBox.getChildren().add(notesCard(recommendation, result));
        status.setText(converter.converts() && typedBudget != null
            ? recommendation.mode().label() + ": " + recommendation.itemCount() + " item(s), "
                + money(recommendation.breakdown().total()) + " of "
                + converter.formatDisplay(typedBudget)
                + " (searched within " + BudgetMoney.format(recommendation.breakdown().budget(),
                    recommendation.breakdown().currency()) + " of recorded prices)"
            : recommendation.headline());
    }

    /** A signed amount in the currency on screen, for the change report. */
    private String signed(BigDecimal catalogueChange) {
        if (catalogueChange.signum() == 0) {
            return money(BigDecimal.ZERO);
        }
        return (catalogueChange.signum() > 0 ? "+ " : "\u2212 ") + money(catalogueChange.abs());
    }

    /** "What changed since your last optimisation" — the result of the what-if comparison. */
    private Node deltaCard(RecommendationDelta delta) {
        Label title = label("CHANGES SINCE YOUR LAST OPTIMISATION", "eyebrow");
        VBox rows = new VBox(3);
        for (String change : delta.productChanges()) {
            Label item = label("\u2022  " + change, "muted");
            item.setWrapText(true);
            rows.getChildren().add(item);
        }
        String totalChange = converter.converts()
            ? signed(delta.totalChange()) + (delta.totalChange().signum() > 0 ? " more" : " less") + " expensive"
            : delta.totalChangeText();
        String remainingChange = converter.converts()
            ? signed(BudgetMoney.scale(delta.remaining().subtract(delta.previousRemaining()))) + " left over"
            : delta.remainingChangeText();
        Label totals = label("Estimated total " + totalChange
            + "  ·  remaining " + remainingChange
            + "  ·  score " + delta.scoreChangeText()
            + "  ·  " + delta.itemChangeText(), "status");
        totals.setWrapText(true);
        VBox box = new VBox(8, title, totals, rows);
        if (delta.noChange()) {
            box.getChildren().add(label("The optimiser returned exactly the same basket for the changed"
                + " request, which is a real result and not a cached one.", "chart-note"));
        }
        box.getStyleClass().add("toolbar-card");
        return box;
    }

    /** Explore Alternatives: pre-built what-if requests, each applied to the form and re-optimised. */
    private Node exploreCard(List<WhatIfOption> options, BudgetRecommendation recommendation) {
        exploreBox.setId("budget-explore");
        exploreBox.getChildren().clear();
        Label title = label("EXPLORE ALTERNATIVES", "eyebrow");
        Label help = label(recommendation == null
            ? "Change one thing and try again. Nothing below relaxes a mandatory rule silently: each option"
            + " is a complete request that the optimiser re-evaluates from scratch."
            : "Each option fills the form above with a changed request and optimises it again, then shows"
            + " exactly what moved. Your mandatory rules stay mandatory unless the option says otherwise."
            + (converter.converts() ? " Amounts follow the currency you chose in step 1." : ""),
            "muted");
        help.setWrapText(true);
        VBox buttons = new VBox(6);
        if (options.isEmpty()) {
            buttons.getChildren().add(label("No what-if variants are available for this request.", "muted"));
        }
        for (WhatIfOption option : options) {
            Button button = new Button(option.label());
            button.getStyleClass().add("ghost-button");
            button.setId("budget-whatif-" + option.label().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-"));
            button.setTooltip(new Tooltip(option.description()));
            button.setOnAction(e -> onWhatIf.accept(option));
            String noteText = option.description();
            if (converter.converts() && renderedRequest != null
                && option.request().budget().compareTo(renderedRequest.budget()) != 0) {
                noteText = "same requirements, planning within " + converter.format(option.request().budget())
                    + " of recorded prices";
            }
            Label note = label(noteText, "chart-note");
            note.setWrapText(true);
            note.setMaxWidth(700);
            buttons.getChildren().add(new VBox(2, button, note));
        }
        exploreBox.getChildren().addAll(title, help, buttons);
        exploreBox.getStyleClass().add("toolbar-card");
        return exploreBox;
    }

    private Node summaryCard(BudgetRecommendation recommendation, BudgetRequest request, OptimizationResult result) {
        HBox kpis = new HBox(12,
            kpi("TOTAL BUDGET", typedBudget != null && converter.converts()
                ? converter.formatDisplay(typedBudget) : money(request.budget()), ""),
            kpi("ESTIMATED TOTAL", money(recommendation.breakdown().total()),
                "kpi-good"),
            kpi("REMAINING", money(recommendation.breakdown().remaining()),
                recommendation.breakdown().remaining().signum() < 0 ? "kpi-warn" : ""),
            kpi("UTILISATION", BudgetMoney.percent(recommendation.breakdown().utilizationPercent()), ""));
        for (Node node : kpis.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        Label mode = label("MODE · " + recommendation.mode().label().toUpperCase(Locale.ROOT), "eyebrow");
        Label score = label("TULITA RECOMMENDATION SCORE  " + BudgetMoney.score(recommendation.averageScore())
            + " average over " + recommendation.itemCount() + " item(s)", "section-title");
        // The mode optimises the *weighted* score, so state that too: the plain average can fall when a
        // basket contains more (cheaper) items, which would otherwise look like a contradiction.
        Label weighted = label("Weighted score for your priorities: "
            + BudgetMoney.score(weightedScore(recommendation, request))
            + " / 100  ·  " + tulita.budget.engine.BudgetOptimizationEngine.objectiveStatement(
                recommendation.mode()), "chart-note");
        weighted.setId("budget-weighted-score");
        weighted.setWrapText(true);
        Label satisfied = label("REQUIRED CATEGORIES SATISFIED · "
            + String.join(", ", recommendation.satisfiedCategories()), "muted");
        satisfied.setWrapText(true);
        Label search = label(result.algorithmNote(), "chart-note");
        search.setWrapText(true);
        VBox box = new VBox(10, mode, score, weighted, kpis, satisfied, search);
        box.getStyleClass().add("chart-card");
        return box;
    }

    /** Delegates to the engine so the metric is testable without a JavaFX toolkit. */
    static double weightedScore(BudgetRecommendation recommendation, BudgetRequest request) {
        return tulita.budget.engine.BudgetOptimizationEngine.weightedScore(recommendation, request);
    }

    private Node chartCard(BudgetRecommendation recommendation, BudgetRequest request) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Recorded amount (" + converter.displayLabel() + ")");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setPrefHeight(280);
        chart.getStyleClass().add("price-chart");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Planned spend");
        recommendation.breakdown().spendByCategory().forEach((category, amount) ->
            series.getData().add(new XYChart.Data<>(category, converter.toDisplay(amount).doubleValue())));
        chart.getData().add(series);
        Label title = label("SPENDING BY CATEGORY", "eyebrow");
        Label note = label("Item subtotal only, shown in " + converter.displayLabel() + ". Delivery is charged once per retailer and the discount is"
            + " applied only when its threshold is met, so the bars cannot double count either."
            + " No savings percentage is shown because this project has no second price baseline to"
            + " compare against.", "chart-note");
        note.setWrapText(true);
        VBox box = new VBox(8, title, chart, note);
        box.getStyleClass().add("chart-card");
        return box;
    }

    private Node selectedCard(BudgetRecommendation recommendation) {
        VBox lines = new VBox(14);
        for (SelectedProduct line : recommendation.selected()) {
            lines.getChildren().add(productLine(line));
        }
        VBox box = new VBox(12, label("SELECTED PRODUCTS", "eyebrow"), lines);
        box.getStyleClass().add("chart-card");
        return box;
    }

    private Node productLine(SelectedProduct line) {
        Node image;
        if (line.candidate().photoPath() != null && catalogueRoot != null) {
            try {
                Path path = CatalogValidator.safe(catalogueRoot, line.candidate().photoPath());
                image = new ImageView(new Image(path.toUri().toString(), 132, 110, true, true));
            } catch (RuntimeException e) {
                image = label("no photo", "muted");
            }
        } else {
            image = label("no photo", "muted");
        }
        Label category = label(line.candidate().category().toUpperCase(Locale.ROOT) + "  ·  "
            + line.candidate().store(), "eyebrow");
        Label name = label(line.candidate().name(), "product-name");
        Label variant = label(line.candidate().brand() + " · model " + line.candidate().model() + " · "
            + line.candidate().variant(), "muted");
        variant.setWrapText(true);
        variant.setMaxWidth(520);
        Label specs = label("Specifications: " + line.candidate().describeSpecs(), "muted");
        specs.setWrapText(true);
        specs.setMaxWidth(520);
        Label explanation = label(line.explanation(), "status");
        explanation.setWrapText(true);
        explanation.setMaxWidth(520);
        Label tradeOff = label(line.tradeOff(), "chart-note");
        tradeOff.setWrapText(true);
        tradeOff.setMaxWidth(520);
        VBox detail = new VBox(5, category, name, variant, specs, explanation, tradeOff);
        HBox.setHgrow(detail, Priority.ALWAYS);

        Label price = label(money(line.lineTotal()), "price");
        Label unit = label(line.quantityLine() + " × "
            + money(line.candidate().unitPrice()), "muted");
        Label score = label(line.scoreText(), "pill-soft");
        Label observed = label(line.candidate().observedAt() == null || line.candidate().observedAt().isBlank()
            ? "observation date not recorded in source" : "observed " + line.candidate().observedAt(), "chart-note");
        VBox money = new VBox(5, price, unit, score, observed);
        money.setAlignment(Pos.TOP_RIGHT);
        money.setMinWidth(210);

        LineCharges charges = renderedCharges.get(line.candidate().signature());
        if (charges != null && charges.hasCharges()) {
            VBox chargeLines = new VBox(1);
            chargeLines.getStyleClass().add("line-charges");
            chargeLines.setId("budget-charges-" + line.candidate().productId());
            for (String text : charges.describeLines(converter.displayCurrency())) {
                boolean isFinal = text.startsWith("final ");
                Label chargeLabel = label(text, isFinal ? "line-final" : "chart-note");
                chargeLines.getChildren().add(chargeLabel);
            }
            money.getChildren().add(3, chargeLines);
        }
        if (line.candidate().url() != null && !line.candidate().url().isBlank()) {
            Hyperlink link = new Hyperlink("Open retailer page");
            link.setId("budget-link-" + line.candidate().productId());
            link.getStyleClass().add("link-button");
            link.setOnAction(e -> opener.accept(line.candidate().url()));
            Label expired = label("recorded link, may have expired", "chart-note");
            money.getChildren().addAll(link, expired);
        }

        HBox row = new HBox(16, image, detail, money);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("photo-cell");
        return row;
    }

    private Node alternativesCard(OptimizationResult result) {
        VBox cards = new VBox(12);
        for (RecommendationAlternative alternative : result.alternatives()) {
            BudgetRecommendation recommendation = alternative.recommendation();
            Label title = label(alternative.label().toUpperCase(Locale.ROOT), "eyebrow");
            Label totals = label(money(recommendation.breakdown().total())
                + "  ·  remaining " + money(recommendation.breakdown().remaining())
                + "  ·  score " + BudgetMoney.score(recommendation.averageScore()), "section-title");
            VBox diffs = new VBox(3);
            for (String difference : alternative.differences()) {
                Label item = label("•  " + difference, "muted");
                item.setWrapText(true);
                diffs.getChildren().add(item);
            }
            VBox good = new VBox(3);
            for (String advantage : alternative.advantages()) {
                Label item = label("+  " + advantage, "status");
                item.setWrapText(true);
                good.getChildren().add(item);
            }
            VBox bad = new VBox(3);
            for (String disadvantage : alternative.disadvantages()) {
                Label item = label("−  " + disadvantage, "kpi-warn");
                item.setWrapText(true);
                bad.getChildren().add(item);
            }
            VBox card = new VBox(7, title, totals, diffs, good, bad);
            card.getStyleClass().add("toolbar-card");
            cards.getChildren().add(card);
        }
        return new VBox(10, label("ALTERNATIVE COMBINATIONS", "eyebrow"),
            label("Only objectives that were actually evaluated are shown, and identical baskets are never"
                + " repeated under a different name.", "muted"), cards);
    }

    private Node notesCard(BudgetRecommendation recommendation, OptimizationResult result) {
        VBox notes = new VBox(3);
        for (String note : recommendation.breakdown().notes()) {
            Label item = label("•  " + note, "muted");
            item.setWrapText(true);
            notes.getChildren().add(item);
        }
        for (String limitation : result.limitations()) {
            Label item = label("•  " + limitation, "chart-note");
            item.setWrapText(true);
            notes.getChildren().add(item);
        }
        Label title = label("HOW THIS TOTAL WAS BUILT · DATA LIMITATIONS", "eyebrow");
        VBox box = new VBox(8, title, notes);
        box.getStyleClass().add("chart-card");
        return box;
    }

    private Node infeasibleCard(OptimizationResult result, BudgetRequest request) {
        Label title = label("NO VALID COMBINATION FOUND", "hero-kicker");
        Label headline = label("Nothing was silently relaxed: every mandatory rule you set is still in force.",
            "section-title");
        headline.setWrapText(true);
        VBox reasons = new VBox(3);
        for (String reason : result.infeasibleReasons()) {
            Label item = label("•  " + reason, "kpi-warn");
            item.setWrapText(true);
            reasons.getChildren().add(item);
        }
        VBox suggestions = new VBox(3);
        for (String suggestion : result.suggestions()) {
            Label item = label("→  " + suggestion, "muted");
            item.setWrapText(true);
            suggestions.getChildren().add(item);
        }
        Label budget = label("Budget: " + money(request.budget())
            + "  ·  " + request.categories().size() + " categor(y/ies) requested  ·  "
            + request.requiredCategories().size() + " mandatory", "muted");
        VBox box = new VBox(10, title, headline, budget, reasons,
            label("WHAT YOU CAN CHANGE", "side-heading"), suggestions);
        box.getStyleClass().add("chart-card");
        return box;
    }

    private VBox kpi(String caption, String value, String styleClass) {
        Label valueLabel = label(value, "kpi-value");
        Label captionLabel = label(caption, "kpi-caption");
        VBox box = new VBox(3, valueLabel, captionLabel);
        box.getStyleClass().add("kpi-box");
        if (!styleClass.isBlank()) {
            box.getStyleClass().add(styleClass);
        }
        box.setMinWidth(170);
        return box;
    }

    public void showSaved(List<SavedBudgetRun> runs) {
        savedBox.setId("budget-saved");
        savedBox.getChildren().clear();
        if (runs.isEmpty()) {
            savedBox.getChildren().add(label("Nothing saved yet.", "muted"));
            return;
        }
        for (SavedBudgetRun run : runs) {
            Label line = label(run.describe(), "muted");
            line.setWrapText(true);
            Button view = new Button("View");
            view.getStyleClass().add("ghost-button");
            view.setOnAction(e -> showSavedRun(run));
            HBox row = new HBox(10, view, line);
            row.setAlignment(Pos.CENTER_LEFT);
            savedBox.getChildren().add(row);
        }
    }

    /** Opens one saved recommendation, including every stored price observation it used. */
    private void showSavedRun(SavedBudgetRun run) {
        VBox body = buildSavedRunDocument(run);
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Saved Tulita recommendation");
        dialog.setHeaderText("Saved recommendation");
        ScrollPane scroller = new ScrollPane(body);
        scroller.setFitToWidth(true);
        scroller.setPrefSize(720, 420);
        dialog.getDialogPane().setContent(scroller);
        dialog.getDialogPane().getStylesheets().addAll(getStylesheets());
        dialog.initOwner(getScene() == null ? null : getScene().getWindow());
        dialog.showAndWait();
    }

    /**
     * The stored detail of one saved recommendation.
     *
     * <p>Public so the viewer can be exercised without opening a modal dialog: it is the same node
     * the dialog shows, and it only ever displays what was recorded at save time.</p>
     */
    public VBox buildSavedRunDocument(SavedBudgetRun run) {
        VBox body = new VBox(8);
        Label head = label(run.mode() + " · " + run.createdAt(), "eyebrow");
        Label totals = label("Budget " + BudgetMoney.format(run.budget(), run.currency())
            + " · estimated total " + BudgetMoney.format(run.total(), run.currency())
            + " · remaining " + BudgetMoney.format(run.remaining(), run.currency()), "status");
        totals.setWrapText(true);
        body.getChildren().addAll(head, totals, new Separator());
        for (SavedBudgetRun.Line line : run.lines()) {
            Label row = label("•  " + line.category() + " — " + line.name() + " x" + line.quantity()
                + " @ " + line.store() + " · " + BudgetMoney.format(line.unitPrice(), run.currency())
                + " each · " + BudgetMoney.format(line.lineTotal(), run.currency()) + " total"
                + (line.observedAt() == null || line.observedAt().isBlank() ? "" : " · observed " + line.observedAt()),
                "muted");
            row.setWrapText(true);
            body.getChildren().add(row);
        }
        body.getChildren().add(new Separator());
        body.getChildren().add(label("Stored limitations", "side-heading"));
        for (String limitation : run.limitations()) {
            Label row = label("•  " + limitation, "chart-note");
            row.setWrapText(true);
            body.getChildren().add(row);
        }
        return body;
    }

    /** Pushes a complete request back into the form, so the form always mirrors what was optimised. */
    public void applyRequest(BudgetRequest request) {
        budgetField.setText(BudgetMoney.scale(request.budget()).toPlainString());
        for (CategoryRow row : rows) {
            BudgetRequest.CategoryRequirement requirement = request.byCategory().get(row.category);
            if (requirement == null) {
                row.required.setSelected(false);
                row.min.getValueFactory().setValue(0);
                row.max.getValueFactory().setValue(1);
                row.brand.getSelectionModel().selectFirst();
                row.priority.getSelectionModel().select("Medium");
                row.minimumSpecValue.clear();
                row.target.clear();
                continue;
            }
            row.required.setSelected(requirement.required());
            row.min.getValueFactory().setValue(requirement.minQuantity());
            row.max.getValueFactory().setValue(requirement.maxQuantity());
            String brand = requirement.preferredBrand() == null ? "Any brand" : requirement.preferredBrand();
            if (!row.brand.getItems().contains(brand)) {
                row.brand.getItems().add(brand);
            }
            row.brand.getSelectionModel().select(brand);
            row.priority.getSelectionModel().select(requirement.priority().label());
            if (requirement.minimumSpec() == null) {
                row.minimumSpecValue.clear();
            } else {
                row.minimumSpec.getSelectionModel().select(requirement.minimumSpec().metric());
                row.minimumSpecValue.setText(BigDecimal.valueOf(requirement.minimumSpec().minimum())
                    .stripTrailingZeros().toPlainString());
            }
            row.target.setText(requirement.targetUnitPrice() == null ? ""
                : requirement.targetUnitPrice().toPlainString());
        }
        RecommendationMode mode = request.mode();
        if (modeButtons.containsKey(mode)) {
            modeButtons.get(mode).setSelected(true);
        }
        request.weights().asText().forEach((criterionName, value) -> {
            for (Map.Entry<ScoringCriterion, Slider> entry : weightSliders.entrySet()) {
                if (entry.getKey().name().equalsIgnoreCase(criterionName)) {
                    entry.getValue().setValue(Double.parseDouble(value));
                }
            }
        });
        for (Map.Entry<String, TextField> entry : deliveryFields.entrySet()) {
            BigDecimal fee = request.deliveryFeeByStore().getOrDefault(entry.getKey(), BudgetMoney.ZERO);
            entry.getValue().setText(BudgetMoney.scale(fee).toPlainString());
        }
        discountPercent.setText(BudgetMoney.scale(request.discountPercent()).toPlainString());
        discountMinimum.setText(BudgetMoney.scale(request.discountMinimumSubtotal()).toPlainString());
        clearValidation();
    }

    /**
     * Opens the platform print dialog for the plan on screen.
     *
     * <p>JavaFX can print a node directly, so no PDF library was added: choose any PDF printer in the
     * dialog to save a PDF, or a real printer to print. Nothing is uploaded and nothing is fabricated —
     * the document is built from the same recommendation the screen shows.</p>
     */
    public void printPlan() {
        if (rendered == null) {
            showError("Optimise a plan first, then print it.");
            return;
        }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showError("This machine reports no print service, so the plan cannot be printed or saved as PDF.");
            return;
        }
        if (!job.showPrintDialog(getScene() == null ? null : getScene().getWindow())) {
            job.endJob();
            return;
        }
        VBox document = buildPrintableDocument();
        new Scene(new Group(document)); // attach to a scene graph so CSS and layout can be applied
        document.applyCss();
        document.layout();
        document.getChildren().forEach(Node::applyCss);
        if (job.printPage(document)) {
            job.endJob();
            status("Sent the plan to " + job.getPrinter().getName() + ".");
        } else {
            job.endJob();
            showError("The printer rejected the page; the plan on screen is unchanged.");
        }
    }

    /** The printable version of the plan: totals, lines with charges, alternatives and limitations. */
    public VBox buildPrintableDocument() {
        VBox page = new VBox(10);
        page.setPrefWidth(520);
        page.setPadding(new Insets(10));
        page.setStyle("-fx-background-color: white;");
        if (rendered == null || renderedRequest == null) {
            page.getChildren().add(label("No recommendation to print.", "muted"));
            return page;
        }
        page.getChildren().add(label("TULITA — Budget Optimizer plan", "dialog-title"));
        page.getChildren().add(label("Mode: " + rendered.mode().label() + " · " + converter.displayCurrency()
            + " · generated from curated historical observations, not live prices", "chart-note"));
        if (converter.converts()) {
            Label assumption = label(converter.describe(), "chart-note");
            assumption.setWrapText(true);
            page.getChildren().add(assumption);
        }
        page.getChildren().add(new Separator());
        page.getChildren().add(label("Budget " + money(rendered.breakdown().budget())
            + " · estimated total " + money(rendered.breakdown().total())
            + " · remaining " + money(rendered.breakdown().remaining())
            + " · utilisation " + BudgetMoney.percent(rendered.breakdown().utilizationPercent()), "status"));
        page.getChildren().add(label("Items", "side-heading"));
        for (SelectedProduct line : rendered.selected()) {
            String charges = renderedCharges.containsKey(line.candidate().signature())
                ? renderedCharges.get(line.candidate().signature()).describe(converter.displayCurrency()) : "";
            Label row = label("•  " + line.candidate().category() + " — " + line.candidate().name()
                + " (" + line.candidate().brand() + ", model " + line.candidate().model() + ")"
                + " x" + line.quantity() + " @ " + line.candidate().store()
                + " · " + money(line.lineTotal())
                + " · score " + line.scoreText() + (charges.isBlank() ? "" : " · " + charges), "muted");
            row.setWrapText(true);
            page.getChildren().add(row);
            Label why = label("    " + line.explanation(), "chart-note");
            why.setWrapText(true);
            page.getChildren().add(why);
        }
        page.getChildren().add(new Separator());
        page.getChildren().add(label("Totals", "side-heading"));
        rendered.breakdown().asText().forEach((key, value) -> page.getChildren().add(label(key + ": " + value, "muted")));
        for (String note : rendered.breakdown().notes()) {
            Label row = label("•  " + note, "chart-note");
            row.setWrapText(true);
            page.getChildren().add(row);
        }
        page.getChildren().add(label("Data limitations", "side-heading"));
        for (String limitation : rendered.limitations()) {
            Label row = label("•  " + limitation, "chart-note");
            row.setWrapText(true);
            page.getChildren().add(row);
        }
        return page;
    }

    /** Called by the controller so a product card can open its recorded retailer page. */
    public void onOpenLink(Consumer<String> opener) {
        this.opener = opener == null ? url -> { } : opener;
    }

    /** Called when the shopper picks one of the Explore Alternatives options. */
    public void onWhatIf(Consumer<WhatIfOption> action) {
        onWhatIf = action == null ? option -> { } : action;
    }

    public void reset() {
        budgetField.clear();
        validation.setText("");
        resultsBox.getChildren().clear();
        save.setDisable(true);
        export.setDisable(true);
        print.setDisable(true);
        rendered = null;
        renderedRequest = null;
        renderedCharges = Map.of();
        typedBudget = null;
        for (CategoryRow row : rows) {
            row.required.setSelected(false);
            row.min.getValueFactory().setValue(0);
            row.max.getValueFactory().setValue(1);
            row.brand.getSelectionModel().selectFirst();
            row.priority.getSelectionModel().select("Medium");
            row.minimumSpecValue.clear();
            row.target.clear();
        }
        applyDefaultSelection();
        status.setText("Form reset.");
    }

    public void onOptimize(Runnable action) {
        onOptimize = action;
    }

    public void onSave(Runnable action) {
        onSave = action;
    }

    public void onExport(Runnable action) {
        onExport = action;
    }

    public void onReset(Runnable action) {
        onReset = action;
    }

    /** Exposed for tests: the last rendered recommendation container. */
    public VBox results() {
        return resultsBox;
    }

    public String currency() {
        return currency;
    }

    private static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    /** Convenience for the controller: the score of a line, used by the saved-run builder. */
    public static double scoreOf(SelectedProduct line) {
        return line.score().total();
    }

    /** Kept so the controller can build a product-score view without touching model internals. */
    public static ProductScore score(SelectedProduct line) {
        return line.score();
    }
}
