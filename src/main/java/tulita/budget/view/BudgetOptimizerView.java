package tulita.budget.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tulita.budget.model.*;
import tulita.budget.service.BudgetDataLoader;
import tulita.util.Money;

import java.math.BigDecimal;
import java.util.*;

/**
 * JavaFX workspace for the budget optimizer. It owns presentation and input parsing only;
 * optimisation, scoring and data loading live in {@code tulita.budget.service}.
 */
public final class BudgetOptimizerView extends BorderPane {
    public interface Actions {
        void optimize(BudgetRequest request);
        void reload();
        void export(BudgetPlan plan);
        void applyAlternative(BudgetAlternative alternative);
    }

    private static final BigDecimal DEFAULT_BUDGET = new BigDecimal("75000");

    private final TextField budgetField = new TextField("75000");
    private final Slider budgetSlider = new Slider(10000, 500000, 75000);
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleButton bestValue = new ToggleButton("Best Value");
    private final ToggleButton lowestCost = new ToggleButton("Lowest Cost");
    private final Map<BudgetCategory, CategoryRow> categoryRows = new EnumMap<>(BudgetCategory.class);
    private final ListView<String> brandList = new ListView<>();
    private final Label source = new Label("Loading historical catalogue…");
    private final Label status = new Label("Starting…");
    private final Label formError = new Label();
    private final Button optimizeButton = new Button("Optimize my budget");
    private final Button exportButton = new Button("Export CSV");
    private final Button reloadButton = new Button("Reload");
    private final ProgressIndicator progress = new ProgressIndicator();

    private final VBox heroBox;
    private final VBox dashboard = new VBox(16);
    private final VBox body = new VBox(16);
    private final Label kpiBudget = new Label("—");
    private final Label kpiTotal = new Label("—");
    private final Label kpiRemaining = new Label("—");
    private final Label kpiUtilization = new Label("—");
    private final Label kpiUtility = new Label("—");

    private Actions actions;
    private BudgetDataLoader.Loaded loaded;
    private BudgetPlan plan;
    private boolean dataReady;
    private boolean busy;
    private boolean syncing;

    public BudgetOptimizerView() {
        getStyleClass().add("workspace");
        heroBox = hero();
        setTop(header());
        setLeft(form());
        setCenter(centre());
        dashboard.getChildren().setAll(heroBox, notice(), emptyState());
        dashboard.setPadding(new Insets(20, 22, 24, 22));
        showDisabledState("Loading the catalogue…", "The planner is reading the same local dataset used by Tulita's dataset screens.");
    }

    private Node header() {
        Label mark = new Label("₹");
        mark.getStyleClass().add("brand-mark");
        Label title = new Label("Budget Optimizer");
        title.getStyleClass().add("brand");
        Label subtitle = new Label("Intelligent, explainable basket planning in INR · historical catalogue prices");
        subtitle.getStyleClass().add("side-tagline");
        VBox titles = new VBox(2, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        source.getStyleClass().add("muted");
        source.setMaxWidth(500);
        source.setTextOverrun(OverrunStyle.ELLIPSIS);
        reloadButton.getStyleClass().add("ghost-button");
        reloadButton.setOnAction(e -> {
            if (actions != null) actions.reload();
        });
        progress.setMaxSize(22, 22);
        progress.setVisible(false);
        progress.setManaged(false);
        HBox header = new HBox(12, mark, titles, spacer, source, progress, reloadButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.getStyleClass().add("budget-header");
        return header;
    }

    private Node form() {
        VBox form = new VBox(13);
        form.getStyleClass().add("budget-form");
        form.setPadding(new Insets(18, 18, 22, 18));

        Label kicker = label("PLAN A SETUP", "side-heading");
        Label heading = label("What do you need?", "section-title");
        Label hint = label("Choose the categories, mark the essentials and set an INR ceiling. The plan never exceeds it.", "muted");
        hint.setWrapText(true);

        Label budgetLabel = label("Budget (INR)", "field-label");
        budgetField.getStyleClass().add("search-field");
        budgetField.setPromptText("e.g. 75000");
        budgetField.setId("budget-field");
        budgetField.textProperty().addListener((obs, old, now) -> {
            if (syncing) return;
            setFormError("");
            BigDecimal parsed = parseBudgetQuietly(now);
            if (parsed != null && parsed.doubleValue() >= budgetSlider.getMin() && parsed.doubleValue() <= budgetSlider.getMax()) {
                syncing = true;
                budgetSlider.setValue(parsed.doubleValue());
                syncing = false;
            }
        });
        budgetSlider.setBlockIncrement(1000);
        budgetSlider.setMajorTickUnit(100000);
        budgetSlider.setMinorTickCount(0);
        budgetSlider.setShowTickMarks(false);
        budgetSlider.setShowTickLabels(false);
        budgetSlider.getStyleClass().add("budget-slider");
        budgetSlider.valueProperty().addListener((obs, old, now) -> {
            if (syncing) return;
            syncing = true;
            budgetField.setText(String.valueOf(now.intValue()));
            syncing = false;
            setFormError("");
        });
        Label sliderHint = label("What-if: drag the budget, then Optimize again. ₹10,000 – ₹5,00,000.", "budget-hint");
        sliderHint.setWrapText(true);
        VBox budgetBox = new VBox(5, budgetLabel, budgetField, budgetSlider, sliderHint);

        Label modeLabel = label("Optimization mode", "field-label");
        bestValue.setToggleGroup(modeGroup);
        lowestCost.setToggleGroup(modeGroup);
        bestValue.getStyleClass().add("mode-toggle");
        lowestCost.getStyleClass().add("mode-toggle");
        bestValue.setSelected(true);
        bestValue.setTooltip(new Tooltip("Maximises specification quality, value for money and your brand preference."));
        lowestCost.setTooltip(new Tooltip("Takes the cheapest recorded products that still satisfy every mandatory category."));
        HBox modes = new HBox(6, bestValue, lowestCost);
        HBox.setHgrow(bestValue, Priority.ALWAYS);
        HBox.setHgrow(lowestCost, Priority.ALWAYS);
        bestValue.setMaxWidth(Double.MAX_VALUE);
        lowestCost.setMaxWidth(Double.MAX_VALUE);
        modeGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        Label categoriesLabel = label("Categories", "field-label");
        VBox categoryBox = new VBox(7);
        for (BudgetCategory category : BudgetCategory.values()) {
            CategoryRow row = categoryRow(category);
            categoryRows.put(category, row);
            categoryBox.getChildren().add(row.node());
        }

        Label brandsLabel = label("Preferred brands · optional", "field-label");
        brandList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        brandList.setPrefHeight(116);
        brandList.setMaxWidth(Double.MAX_VALUE);
        brandList.getStyleClass().add("brand-list");
        brandList.setPlaceholder(label("Brands appear after the catalogue loads.", "muted"));
        Button clearBrands = new Button("Clear");
        clearBrands.getStyleClass().add("link-button");
        clearBrands.setOnAction(e -> brandList.getSelectionModel().clearSelection());
        HBox brandsHead = new HBox(8, brandsLabel, new Region(), clearBrands);
        HBox.setHgrow(brandsHead.getChildren().get(1), Priority.ALWAYS);
        brandsHead.setAlignment(Pos.CENTER_LEFT);

        formError.getStyleClass().add("form-error");
        formError.setWrapText(true);
        formError.setVisible(false);
        formError.setManaged(false);

        optimizeButton.getStyleClass().add("primary-button");
        optimizeButton.setMaxWidth(Double.MAX_VALUE);
        optimizeButton.setId("optimize-button");
        optimizeButton.setOnAction(e -> submit());
        exportButton.getStyleClass().add("secondary-button");
        exportButton.setMaxWidth(Double.MAX_VALUE);
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> {
            if (plan != null && plan.feasible() && actions != null) actions.export(plan);
        });
        HBox actionsRow = new HBox(8, exportButton);
        HBox.setHgrow(exportButton, Priority.ALWAYS);

        Button reset = new Button("Reset");
        reset.getStyleClass().add("ghost-button");
        reset.setOnAction(e -> resetForm());
        HBox buttons = new HBox(8, optimizeButton, reset);
        HBox.setHgrow(optimizeButton, Priority.ALWAYS);
        optimizeButton.setMaxWidth(Double.MAX_VALUE);

        Separator sep = new Separator();
        Label safety = label("No fabricated products, discounts or availability claims. Prices are observed records, not live quotes.", "budget-footnote");
        safety.setWrapText(true);

        status.getStyleClass().add("budget-status");
        status.setWrapText(true);
        form.getChildren().addAll(kicker, heading, hint, budgetBox, modeLabel, modes, categoriesLabel, categoryBox,
                brandsHead, brandList, formError, buttons, actionsRow, status, sep, safety);
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("budget-form-scroll");
        scroll.setPrefWidth(350);
        scroll.setMinWidth(310);
        return scroll;
    }

    private CategoryRow categoryRow(BudgetCategory category) {
        CheckBox selected = new CheckBox(category.label());
        selected.getStyleClass().add("category-check");
        selected.setSelected(category == BudgetCategory.LAPTOPS || category == BudgetCategory.KEYBOARDS
                || category == BudgetCategory.MICE || category == BudgetCategory.HEADPHONES);
        Spinner<Integer> quantity = new Spinner<>(1, CategoryRequirement.MAX_QUANTITY, 1);
        quantity.setPrefWidth(56);
        quantity.setMaxWidth(56);
        quantity.setTooltip(new Tooltip("Units to buy. The optimizer may repeat the same recorded product."));
        CheckBox required = new CheckBox("Required");
        required.getStyleClass().add("required-check");
        required.setSelected(category == BudgetCategory.LAPTOPS || category == BudgetCategory.KEYBOARDS);
        Label count = label("—", "budget-count");
        count.setMinWidth(70);
        count.setAlignment(Pos.CENTER_RIGHT);

        quantity.disableProperty().bind(selected.selectedProperty().not());
        required.disableProperty().bind(selected.selectedProperty().not());
        selected.selectedProperty().addListener((obs, old, now) -> {
            setFormError("");
            if (!now) required.setSelected(false);
        });

        HBox top = new HBox(7, selected, new Region(), quantity, required);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox row = new HBox(7, top, count);
        HBox.setHgrow(top, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox node = new VBox(2, row);
        node.getStyleClass().add("budget-category-row");
        return new CategoryRow(selected, quantity, required, count, node);
    }

    private Node centre() {
        dashboard.getStyleClass().add("workspace");
        ScrollPane scroll = new ScrollPane(dashboard);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("main-scroll");
        return scroll;
    }

    private VBox hero() {
        Label kicker = label("INTELLIGENT BUDGET OPTIMIZER", "hero-kicker");
        Label title = label("Build a complete setup, without crossing your budget.", "hero-title");
        title.setWrapText(true);
        Label subtitle = label("Observed catalogue prices · mandatory categories protected · every rupee explained", "hero-subtitle");
        subtitle.setWrapText(true);
        if (!kpiBudget.getStyleClass().contains("kpi-value")) kpiBudget.getStyleClass().add("kpi-value");
        if (!kpiTotal.getStyleClass().contains("kpi-value")) kpiTotal.getStyleClass().add("kpi-value");
        if (!kpiRemaining.getStyleClass().contains("kpi-value")) kpiRemaining.getStyleClass().add("kpi-value");
        if (!kpiUtilization.getStyleClass().contains("kpi-value")) kpiUtilization.getStyleClass().add("kpi-value");
        if (!kpiUtility.getStyleClass().contains("kpi-value")) kpiUtility.getStyleClass().add("kpi-value");
        HBox kpis = new HBox(10, kpiTile("BUDGET", kpiBudget), kpiTile("TOTAL COST", kpiTotal),
                kpiTile("REMAINING", kpiRemaining), kpiTile("UTILISATION", kpiUtilization), kpiTile("UTILITY / 100", kpiUtility));
        for (Node node : kpis.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
        VBox hero = new VBox(8, kicker, title, subtitle, kpis);
        hero.getStyleClass().add("hero");
        hero.setPadding(new Insets(24, 26, 22, 26));
        return hero;
    }

    private VBox kpiTile(String caption, Label value) {
        Label c = label(caption, "kpi-caption");
        VBox box = new VBox(2, value, c);
        box.getStyleClass().add("kpi-tile");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Node notice() {
        Label notice = label("Data honesty: these are historical observations from the bundled catalogue. Delivery, tax, condition guarantees and live availability are unknown. The planner never invents a product, price or discount.", "history-notice");
        notice.setWrapText(true);
        return notice;
    }

    private VBox emptyState() {
        Label icon = label("◎", "budget-state-icon");
        Label title = label("Your plan will appear here.", "section-title");
        Label copy = label("Pick categories on the left, enter a budget in rupees and press Optimize. Required categories are never dropped; optional categories are added only when they fit.", "muted");
        copy.setWrapText(true);
        copy.setMaxWidth(620);
        VBox box = new VBox(10, icon, title, copy);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(46, 30, 46, 30));
        box.getStyleClass().add("budget-state");
        return box;
    }

    private void submit() {
        if (actions == null) return;
        try {
            BudgetRequest request = request();
            setFormError("");
            actions.optimize(request);
        } catch (IllegalArgumentException ex) {
            setFormError(ex.getMessage());
        }
    }

    public BudgetRequest request() {
        String text = budgetField.getText() == null ? "" : budgetField.getText().strip();
        if (text.startsWith("-")) throw new IllegalArgumentException("Budget must be greater than ₹0.");
        String cleaned = text.replace("₹", "").replace(",", "").replace(" ", "");
        if (cleaned.isBlank()) throw new IllegalArgumentException("Enter a budget in INR.");
        BigDecimal amount;
        try {
            amount = new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Budget must be a number in INR, for example 75,000.");
        }
        if (amount.signum() <= 0) throw new IllegalArgumentException("Budget must be greater than ₹0.");

        List<CategoryRequirement> requirements = new ArrayList<>();
        for (BudgetCategory category : BudgetCategory.values()) {
            CategoryRow row = categoryRows.get(category);
            if (!row.selected().isSelected()) continue;
            requirements.add(new CategoryRequirement(category, row.required().isSelected(), row.quantity().getValue()));
        }
        if (requirements.isEmpty()) throw new IllegalArgumentException("Select at least one category.");
        PlanMode mode = bestValue.isSelected() ? PlanMode.BEST_VALUE : PlanMode.LOWEST_COST;
        Set<String> preferred = new LinkedHashSet<>(brandList.getSelectionModel().getSelectedItems());
        preferred.removeIf(Objects::isNull);
        return new BudgetRequest(amount, requirements, mode, preferred);
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    public void loaded(BudgetDataLoader.Loaded loaded) {
        this.loaded = loaded;
        this.dataReady = true;
        source.setText(loaded.sourceLabel() + " · " + loaded.catalog().totalProducts() + " matching records");
        for (BudgetCategory category : BudgetCategory.values()) {
            CategoryRow row = categoryRows.get(category);
            int count = loaded.catalog().count(category);
            row.count().setText(count == 0 ? "No records" : count + " records");
            if (count == 0) {
                row.selected().setSelected(false);
                row.selected().setDisable(true);
            } else {
                row.selected().setDisable(false);
            }
        }
        List<String> brands = new ArrayList<>(loaded.catalog().brands());
        if (brands.size() > 40) brands = new ArrayList<>(brands.subList(0, 40));
        brandList.getItems().setAll(brands);
        setBusy(false);
        if (loaded.catalog().totalProducts() == 0) {
            showError("The configured catalogue loaded, but no laptop, keyboard, mouse or headphone rows were recognised. "
                    + "Check that the multistore catalog.csv.zip is complete; no demo rows are substituted.");
            return;
        }
        status.setText("Catalogue ready. Set a budget and optimize.");
        updateDisabledState();
    }

    public void showLoadingData() {
        dataReady = false;
        plan = null;
        exportButton.setDisable(true);
        setKpisToDash();
        source.setText("Loading historical catalogue…");
        status.setText("Loading catalogue…");
        setFormError("");
        setBusy(true);
        body.getChildren().setAll(emptyState());
        dashboard.getChildren().setAll(heroBox, notice(), body);
    }

    public void showError(String message) {
        dataReady = false;
        plan = null;
        setKpisToDash();
        exportButton.setDisable(true);
        source.setText("Catalogue unavailable");
        setBusy(false);
        status.setText("Setup required");
        setFormError(message);
        Label title = label("Catalogue could not be loaded", "section-title");
        Label copy = label(message, "muted");
        copy.setWrapText(true);
        VBox card = new VBox(8, title, copy);
        card.getStyleClass().add("budget-warning-card");
        body.getChildren().setAll(card);
        dashboard.getChildren().setAll(heroBox, notice(), body);
    }

    public void showPlan(BudgetPlan plan) {
        this.plan = plan;
        if (plan == null) {
            body.getChildren().setAll(emptyState());
            return;
        }
        kpiBudget.setText(Money.inr(plan.budget()));
        kpiTotal.setText(Money.inr(plan.totalCost()));
        kpiRemaining.setText(Money.inr(plan.remaining()));
        kpiUtilization.setText(plan.utilizationPercent().toPlainString() + "%");
        kpiUtility.setText(String.valueOf(plan.utility()));
        exportButton.setDisable(!plan.feasible() || busy);
        if (plan.feasible()) {
            status.setText("Plan ready · " + plan.itemCount() + " item(s) · " + Money.inr(plan.totalCost())
                    + " of " + Money.inr(plan.budget()) + " · " + plan.utilizationPercent().toPlainString() + "% used");
            body.getChildren().setAll(itemsCard(plan), spendCard(plan), explanationCard(plan),
                    warningsCard(plan), alternativesCard(plan));
        } else {
            status.setText("No feasible basket for this budget. Adjust the budget or requirements.");
            body.getChildren().setAll(infeasibleCard(plan));
        }
        dashboard.getChildren().setAll(heroBox, notice(), body);
    }

    private VBox infeasibleCard(BudgetPlan plan) {
        Label title = label("This basket cannot be built within " + Money.inr(plan.budget()) + ".", "section-title");
        VBox reasons = new VBox(6);
        for (String warning : plan.warnings()) reasons.getChildren().add(bullet(warning, "budget-reason"));
        VBox card = new VBox(10, title, reasons);
        card.getStyleClass().add("budget-warning-card");
        return card;
    }

    private VBox itemsCard(BudgetPlan plan) {
        Label title = label("Selected products", "section-title");
        Label subtitle = label(plan.itemCount() + " item(s) · line totals use recorded unit prices; no bulk discount is assumed.", "muted");
        VBox list = new VBox(9);
        for (BudgetSelection selection : plan.selections()) list.getChildren().add(productRow(selection));
        VBox card = new VBox(10, title, subtitle, list);
        card.getStyleClass().add("budget-card");
        return card;
    }

    private Node productRow(BudgetSelection selection) {
        BudgetProduct product = selection.product();
        Label chip = label(product.category().label(), "budget-chip");
        Label title = label(product.title(), "product-name");
        title.setWrapText(true);
        title.setTooltip(new Tooltip(product.title()));
        String brand = product.brand().isBlank() || "Not stated".equals(product.brand()) ? "Brand not stated" : product.brand();
        Label meta = label(brand + " · " + product.store() + " · " + product.observedLabel(), "muted");
        meta.setWrapText(true);
        VBox names = new VBox(4, title, meta);
        HBox.setHgrow(names, Priority.ALWAYS);

        Label lineTotal = label(Money.inr(selection.lineTotal()), "price");
        String unit = selection.quantity() > 1
                ? selection.quantity() + " × " + Money.inr(product.price()) + " · recorded unit price"
                : "unit price " + Money.inr(product.price());
        Label unitLabel = label(unit, "card-caption");
        unitLabel.setWrapText(true);
        VBox price = new VBox(1, lineTotal, unitLabel);
        price.setAlignment(Pos.TOP_RIGHT);
        price.setMinWidth(150);

        HBox top = new HBox(10, chip, names, price);
        top.setAlignment(Pos.TOP_LEFT);
        HBox scores = new HBox(6,
                label("Quality " + selection.scored().qualityScore() + "/100", "pill-soft"),
                label("Value " + selection.scored().valueScore() + "/100", "pill-soft"),
                label("Value rank " + selection.scored().valueRank() + "/" + selection.scored().valueTotal(), "pill-soft"));
        if (selection.quantity() > 1) scores.getChildren().add(label("Qty " + selection.quantity(), "badge-save"));
        if (product.renewed()) scores.getChildren().add(label("Renewed", "badge-warn"));

        VBox reasons = new VBox(4);
        for (String reason : selection.reasons()) reasons.getChildren().add(bullet(reason, "budget-reason"));
        TitledPane why = new TitledPane("Why this product?", reasons);
        why.setExpanded(false);
        why.getStyleClass().add("why-pane");

        VBox row = new VBox(7, top, scores, why);
        row.getStyleClass().add("budget-product");
        return row;
    }

    private VBox spendCard(BudgetPlan plan) {
        Label title = label("Category-wise spend", "section-title");
        VBox rows = new VBox(7);
        for (BudgetCategory category : BudgetCategory.values()) {
            BigDecimal amount = plan.categorySpend().get(category);
            if (amount == null) continue;
            Label name = label(category.label(), "budget-spend-name");
            name.setMinWidth(92);
            double progressValue = plan.budget().signum() == 0 ? 0 : amount.doubleValue() / plan.budget().doubleValue();
            ProgressBar bar = new ProgressBar(Math.max(0, Math.min(1, progressValue)));
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.getStyleClass().add("budget-bar");
            HBox.setHgrow(bar, Priority.ALWAYS);
            Label value = label(Money.inr(amount), "budget-spend-value");
            value.setMinWidth(130);
            value.setAlignment(Pos.CENTER_RIGHT);
            rows.getChildren().add(new HBox(9, name, bar, value));
        }
        VBox card = new VBox(10, title, rows);
        card.getStyleClass().add("budget-card");
        return card;
    }

    private VBox explanationCard(BudgetPlan plan) {
        Label title = label("Why this plan?", "section-title");
        VBox bullets = new VBox(5);
        for (String explanation : plan.explanations()) bullets.getChildren().add(bullet(explanation, "budget-reason"));
        VBox card = new VBox(9, title, bullets);
        card.getStyleClass().add("budget-card");
        return card;
    }

    private VBox warningsCard(BudgetPlan plan) {
        List<String> warnings = plan.warnings();
        if (warnings.isEmpty()) return new VBox();
        Label title = label("Data notes & limitations", "section-title");
        VBox bullets = new VBox(5);
        for (String warning : warnings) bullets.getChildren().add(bullet(warning, "budget-reason"));
        VBox card = new VBox(8, title, bullets);
        card.getStyleClass().add("budget-note-card");
        return card;
    }

    private VBox alternativesCard(BudgetPlan plan) {
        Label title = label("Alternative combinations", "section-title");
        Label subtitle = label("Every alternative is checked against the same budget and mandatory requirements.", "muted");
        VBox list = new VBox(8);
        if (plan.alternatives().isEmpty()) {
            list.getChildren().add(label("No alternative combination fits this budget. Every tested swap would exceed the ceiling or reduce utility.", "muted"));
        } else {
            for (BudgetAlternative alternative : plan.alternatives()) {
                Label name = label(alternative.label(), "alt-title");
                name.setWrapText(true);
                Label detail = label(alternative.description() + " · " + alternative.itemCount() + " item(s)", "muted");
                detail.setWrapText(true);
                VBox text = new VBox(3, name, detail);
                HBox.setHgrow(text, Priority.ALWAYS);
                Button use = new Button("Use this plan");
                use.getStyleClass().add("secondary-button");
                use.setOnAction(e -> {
                    if (actions != null) actions.applyAlternative(alternative);
                });
                HBox row = new HBox(10, text, use);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("budget-alt-row");
                list.getChildren().add(row);
            }
        }
        VBox card = new VBox(9, title, subtitle, list);
        card.getStyleClass().add("budget-card");
        return card;
    }

    public BudgetPlan currentPlan() {
        return plan;
    }

    public void optimizing() {
        status.setText("Optimizing plan…");
        setFormError("");
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        progress.setVisible(busy);
        progress.setManaged(busy);
        updateDisabledState();
    }

    private void updateDisabledState() {
        boolean ready = dataReady && !busy;
        optimizeButton.setDisable(!ready);
        reloadButton.setDisable(busy);
        exportButton.setDisable(!ready || plan == null || !plan.feasible());
    }

    public void setFormError(String message) {
        boolean show = message != null && !message.isBlank();
        formError.setText(show ? message : "");
        formError.setVisible(show);
        formError.setManaged(show);
    }

    private void resetForm() {
        budgetField.setText(DEFAULT_BUDGET.toPlainString());
        budgetSlider.setValue(DEFAULT_BUDGET.doubleValue());
        bestValue.setSelected(true);
        brandList.getSelectionModel().clearSelection();
        for (BudgetCategory category : BudgetCategory.values()) {
            CategoryRow row = categoryRows.get(category);
            boolean wanted = category == BudgetCategory.LAPTOPS || category == BudgetCategory.KEYBOARDS
                    || category == BudgetCategory.MICE || category == BudgetCategory.HEADPHONES;
            row.selected().setSelected(wanted && !row.selected().isDisabled());
            row.required().setSelected((category == BudgetCategory.LAPTOPS || category == BudgetCategory.KEYBOARDS) && !row.selected().isDisabled());
            row.quantity().getValueFactory().setValue(1);
        }
        setFormError("");
        plan = null;
        setKpisToDash();
        exportButton.setDisable(true);
        body.getChildren().setAll(emptyState());
        dashboard.getChildren().setAll(heroBox, notice(), body);
        status.setText("Form reset.");
    }

    private void showDisabledState(String statusText, String copy) {
        status.setText(statusText);
        Label title = label("Preparing the catalogue", "section-title");
        Label text = label(copy, "muted");
        text.setWrapText(true);
        VBox box = new VBox(8, title, text);
        box.setPadding(new Insets(30));
        box.getStyleClass().add("budget-state");
        body.getChildren().setAll(box);
        dashboard.getChildren().setAll(heroBox, notice(), body);
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        if (style != null && !style.isBlank()) label.getStyleClass().add(style);
        return label;
    }

    private static Label bullet(String text, String style) {
        Label label = new Label("•  " + text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }

    private void setKpisToDash() {
        kpiBudget.setText("—");
        kpiTotal.setText("—");
        kpiRemaining.setText("—");
        kpiUtilization.setText("—");
        kpiUtility.setText("—");
    }

    private static BigDecimal parseBudgetQuietly(String text) {
        if (text == null) return null;
        String cleaned = text.replace("₹", "").replace(",", "").replace(" ", "").strip();
        if (cleaned.isBlank() || cleaned.startsWith("-")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record CategoryRow(CheckBox selected, Spinner<Integer> quantity, CheckBox required, Label count, VBox node) { }
}
