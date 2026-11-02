package tulita.budget.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * One selected product line with a quantity (1–3) and the reasons it earned a place in the basket.
 * Prices are per unit, so {@link #lineTotal()} multiplies the recorded unit price by the quantity
 * without inventing any discount.
 */
public record BudgetSelection(
        ScoredProduct scored,
        int quantity,
        int utility,
        List<String> reasons,
        List<ScoredProduct> alternatives) {

    public BudgetSelection {
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1.");
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
        alternatives = List.copyOf(alternatives == null ? List.of() : alternatives);
    }

    public BudgetSelection(ScoredProduct scored, int utility, List<String> reasons, List<ScoredProduct> alternatives) {
        this(scored, 1, utility, reasons, alternatives);
    }

    public BudgetProduct product() {
        return scored.product();
    }

    public BigDecimal lineTotal() {
        return product().price().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public int lineUtility() {
        return utility * quantity;
    }
}
