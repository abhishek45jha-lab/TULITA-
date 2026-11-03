package tulita.budget.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A recommendation saved to disk so it can be reopened later.
 *
 * <p>Only the decision is stored: the request that produced it, the chosen products with the price
 * observation each line used, the calculated totals and the timestamp. Catalogue products are stored by
 * reference (id + retailer), never duplicated, so a later reload still compares against the same source
 * of truth.</p>
 */
public record SavedBudgetRun(
    String id,
    String createdAt,
    String mode,
    String currency,
    BigDecimal budget,
    BigDecimal itemsSubtotal,
    BigDecimal deliveryCharges,
    BigDecimal discount,
    BigDecimal total,
    BigDecimal remaining,
    String weights,
    List<Line> lines,
    List<String> limitations) {

    public record Line(String category, String productId, String name, String store, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal, double score, String observedAt) {
    }

    public SavedBudgetRun {
        lines = List.copyOf(lines);
        limitations = List.copyOf(limitations);
    }

    public static SavedBudgetRun of(String mode, String currency, BigDecimal budget, BigDecimal itemsSubtotal,
                                    BigDecimal delivery, BigDecimal discount, BigDecimal total,
                                    BigDecimal remaining, String weights, List<Line> lines,
                                    List<String> limitations) {
        String created = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new SavedBudgetRun(created, created, mode, currency, budget, itemsSubtotal, delivery, discount,
            total, remaining, weights, lines, limitations);
    }

    public String describe() {
        return createdAt + " · " + mode + " · " + currency + " " + total + " of " + budget + " · "
            + lines.size() + " line(s)";
    }
}
