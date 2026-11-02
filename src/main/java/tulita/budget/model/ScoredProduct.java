package tulita.budget.model;

import java.util.List;
import java.util.Objects;

/** A catalogue candidate with deterministic, explainable quality and value scores. */
public record ScoredProduct(
        BudgetProduct product,
        int qualityScore,
        int valueScore,
        int valueRank,
        int valueTotal,
        List<ScoreFactor> factors) {

    public ScoredProduct {
        Objects.requireNonNull(product, "product");
        if (qualityScore < 0 || qualityScore > 100) throw new IllegalArgumentException("Quality score must be 0–100.");
        if (valueScore < 0 || valueScore > 100) throw new IllegalArgumentException("Value score must be 0–100.");
        if (valueRank < 1 || valueTotal < 1 || valueRank > valueTotal)
            throw new IllegalArgumentException("Invalid value rank.");
        factors = List.copyOf(factors == null ? List.of() : factors);
    }

    public String valueNote() {
        return "value rank " + valueRank + " of " + valueTotal + " " + product.category().label().toLowerCase();
    }
}
