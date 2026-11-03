package tulita.budget.model;

import java.util.List;
import java.util.Map;

/**
 * A complete, self-contained recommendation: what to buy, what it costs, why, and what the data
 * could not tell us.
 *
 * @param scoreSum          sum of (score x units x priority) over all lines
 * @param averageScore      0..1 mean score of the selected products
 * @param satisfiedCategories category names that received at least their minimum quantity
 */
public record BudgetRecommendation(
    RecommendationMode mode,
    List<SelectedProduct> selected,
    BudgetBreakdown breakdown,
    double scoreSum,
    double averageScore,
    List<String> satisfiedCategories,
    List<String> limitations,
    String headline) {

    public BudgetRecommendation {
        selected = List.copyOf(selected);
        satisfiedCategories = List.copyOf(satisfiedCategories);
        limitations = List.copyOf(limitations);
    }

    public String signature() {
        StringBuilder out = new StringBuilder(mode.name().length() > 0 ? "" : "");
        selected.stream()
            .sorted(java.util.Comparator.comparing(s -> s.candidate().productId()))
            .forEach(s -> out.append(s.candidate().signature()).append('x').append(s.quantity()).append(';'));
        return out.toString();
    }

    public int itemCount() {
        return selected.stream().mapToInt(SelectedProduct::quantity).sum();
    }

    public Map<String, java.math.BigDecimal> spendByCategory() {
        return breakdown.spendByCategory();
    }
}
