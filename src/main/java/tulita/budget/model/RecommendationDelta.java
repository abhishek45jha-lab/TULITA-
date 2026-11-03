package tulita.budget.model;

import tulita.budget.service.BudgetMoney;

import java.math.BigDecimal;
import java.util.List;

/**
 * What changed between the recommendation the shopper was looking at and the one just produced.
 *
 * <p>This is what makes the "Explore Alternatives" panel a what-if analysis rather than a silent
 * recalculation: every number the shopper can compare is stored, including the direction of each change.</p>
 */
public record RecommendationDelta(
    BigDecimal previousTotal,
    BigDecimal total,
    BigDecimal previousRemaining,
    BigDecimal remaining,
    double previousAverageScore,
    double averageScore,
    int previousItems,
    int items,
    List<String> productChanges,
    boolean sameBasket,
    String currency) {

    public RecommendationDelta {
        previousTotal = BudgetMoney.scale(previousTotal);
        total = BudgetMoney.scale(total);
        previousRemaining = BudgetMoney.scale(previousRemaining);
        remaining = BudgetMoney.scale(remaining);
        productChanges = List.copyOf(productChanges);
    }

    /** Signed change in the estimated total; negative means the new plan is cheaper. */
    public BigDecimal totalChange() {
        return BudgetMoney.scale(total.subtract(previousTotal));
    }

    public String totalChangeText() {
        BigDecimal change = totalChange();
        if (change.signum() == 0) {
            return "unchanged at " + BudgetMoney.format(total, currency);
        }
        return (change.signum() > 0 ? "+ " : "\u2212 ")
            + BudgetMoney.format(change.abs(), currency)
            + (change.signum() > 0 ? " more" : " less") + " expensive";
    }

    public String remainingChangeText() {
        BigDecimal change = BudgetMoney.scale(remaining.subtract(previousRemaining));
        if (change.signum() == 0) {
            return "unchanged at " + BudgetMoney.format(remaining, currency);
        }
        return (change.signum() > 0 ? "+ " : "\u2212 ") + BudgetMoney.format(change.abs(), currency)
            + (change.signum() > 0 ? " left over" : " less left over");
    }

    public String scoreChangeText() {
        double change = (averageScore - previousAverageScore) * 100.0;
        if (Math.abs(change) < 0.05) {
            return "unchanged at " + BudgetMoney.score(averageScore);
        }
        return (change > 0 ? "+ " : "\u2212 ")
            + BigDecimal.valueOf(Math.abs(change)).setScale(1, BudgetMoney.ROUNDING).toPlainString()
            + " points, now " + BudgetMoney.score(averageScore);
    }

    public String itemChangeText() {
        if (items == previousItems) {
            return previousItems + " item(s), unchanged";
        }
        return previousItems + " \u2192 " + items + " item(s)";
    }

    /** True when nothing at all moved, so the panel can say so instead of showing noise. */
    public boolean noChange() {
        return sameBasket && totalChange().signum() == 0
            && remainingChangeText().startsWith("unchanged");
    }
}
