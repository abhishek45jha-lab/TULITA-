package tulita.budget.service;

import tulita.budget.model.*;
import tulita.util.Money;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a plan exactly as calculated: summary, selected lines, reasons for each line and the
 * alternative combinations. No additional product, price or discount is introduced.
 */
public final class BudgetCsvExporter {
    private static final String[] HEADER = {
            "section", "category", "product", "brand", "store", "unit_price_inr", "quantity",
            "line_total_inr", "quality_score", "value_score", "value_rank", "observed_at", "url", "detail"
    };

    private BudgetCsvExporter() { }

    public static void export(Path path, BudgetPlan plan, BudgetDataLoader.Loaded loaded) throws IOException {
        if (plan == null || !plan.feasible()) throw new IOException("There is no feasible plan to export.");
        List<String[]> rows = new ArrayList<>();
        rows.add(HEADER);
        summary(rows, plan, loaded);
        for (BudgetSelection selection : plan.selections()) {
            BudgetProduct product = selection.product();
            rows.add(new String[]{
                    "Product", product.category().label(), product.title(), product.brand(), product.store(),
                    plain(product.price()), String.valueOf(selection.quantity()), plain(selection.lineTotal()),
                    String.valueOf(selection.scored().qualityScore()), String.valueOf(selection.scored().valueScore()),
                    selection.scored().valueRank() + "/" + selection.scored().valueTotal(),
                    product.observedAt(), product.sourceUrl(), String.join(" | ", selection.reasons())
            });
        }
        for (BudgetAlternative alternative : plan.alternatives()) {
            rows.add(new String[]{
                    "Alternative", "", alternative.label(), "", "", "", String.valueOf(alternative.itemCount()),
                    plain(alternative.totalCost()), "", "", "", "", "",
                    alternative.description() + " | delta " + Money.inr(alternative.deltaCost())
                            + " | delta utility " + alternative.deltaUtility()
            });
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            for (String[] row : rows) writer.write(join(row) + System.lineSeparator());
        }
    }

    private static void summary(List<String[]> rows, BudgetPlan plan, BudgetDataLoader.Loaded loaded) {
        add(rows, "Mode", plan.mode().label());
        add(rows, "Budget (INR)", plain(plan.budget()));
        add(rows, "Total cost (INR)", plain(plan.totalCost()));
        add(rows, "Remaining (INR)", plain(plan.remaining()));
        add(rows, "Utilisation (%)", plan.utilizationPercent().toPlainString());
        add(rows, "Items (units)", String.valueOf(plan.itemCount()));
        add(rows, "Utility / 100", String.valueOf(plan.utility()));
        add(rows, "Optimal", plan.optimal() ? "yes" : "best found (bounded search)");
        add(rows, "Source", loaded.snapshot().sourceTitle() + " · " + loaded.snapshot().storage());
        add(rows, "Source records", String.valueOf(loaded.snapshot().products().size()));
        add(rows, "Observed range", loaded.snapshot().firstObserved() + " to " + loaded.snapshot().lastObserved());
        add(rows, "Licence", loaded.snapshot().license());
        add(rows, "Data note", "Historical observed prices only; delivery, tax, condition and live availability are unknown.");
        for (String explanation : plan.explanations()) add(rows, "Explanation", explanation);
        for (String warning : plan.warnings()) add(rows, "Warning", warning);
    }

    private static void add(List<String[]> rows, String field, String value) {
        rows.add(new String[]{"Summary", "", field, "", "", "", "", "", "", "", "", "", "", value});
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String join(String[] cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) line.append(',');
            line.append(escape(cells[i]));
        }
        return line.toString();
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
