package tulita.budget.model;

import tulita.budget.service.BudgetMoney;

import java.math.BigDecimal;
import java.util.Locale;

/** One line of a recommendation: the chosen candidate, how many, why, and what it costs. */
public record SelectedProduct(
    ProductCandidate candidate,
    int quantity,
    BigDecimal lineTotal,
    ProductScore score,
    double priorityFactor,
    String explanation,
    String tradeOff) {

    public SelectedProduct {
        if (quantity < 1) {
            throw new IllegalArgumentException("A selected product needs at least one unit.");
        }
        lineTotal = BudgetMoney.scale(lineTotal);
        if (priorityFactor <= 0) {
            throw new IllegalArgumentException("A category priority factor must be positive.");
        }
    }

    public static SelectedProduct of(ProductCandidate candidate, int quantity, ProductScore score,
                                     double priorityFactor, String explanation, String tradeOff) {
        return new SelectedProduct(candidate, quantity, BudgetMoney.multiply(candidate.unitPrice(), quantity),
            score, priorityFactor, explanation, tradeOff);
    }

    /**
     * Contribution of this line to the combination score: score x units x category priority.
     *
     * <p>The priority factor makes a HIGH priority category push the ranking harder than a LOW one,
     * which is the only effect category priority has on the result.</p>
     */
    public double weightedScore() {
        return score.total() * quantity * priorityFactor;
    }

    public String quantityLine() {
        return quantity == 1 ? "1 unit" : quantity + " units";
    }

    public String scoreText() {
        return String.format(Locale.ROOT, "%.1f", score.total() * 100) + " / 100";
    }
}
