package tulita.budget.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The TULITA Recommendation Score of one candidate, with the components that produced it.
 *
 * <p>The score is <em>not</em> a claim about absolute product quality. It is a transparent weighted
 * average of the criteria the catalogue can actually support, normalised to 0..1 and shown as a
 * score out of 100.</p>
 *
 * @param components  normalised 0..1 value of each criterion (0.5 means "unknown, treated as
 *                    neutral" for specifications, value and warranty)
 * @param weights     the raw user weight of each criterion (0-100)
 * @param total       weighted sum in 0..1
 * @param specAvailable false when the catalogue records no comparable specification for this
 *                    product, in which case the specification and value criteria are neutral
 * @param brandKnown  false when the shopper did not ask for a brand, so the brand weight was dropped
 */
public record ProductScore(
    ProductCandidate candidate,
    Map<ScoringCriterion, Double> components,
    Map<ScoringCriterion, Integer> weights,
    double total,
    boolean specAvailable,
    boolean brandKnown,
    List<String> notes) {

    public ProductScore {
        components = Map.copyOf(components);
        weights = Map.copyOf(weights);
        notes = List.copyOf(notes);
    }

    public double component(ScoringCriterion criterion) {
        return components.getOrDefault(criterion, 0.5);
    }

    public static ProductScore unavailable(ProductCandidate candidate) {
        return new ProductScore(candidate, new EnumMap<>(ScoringCriterion.class),
            new EnumMap<>(ScoringCriterion.class), 0.0, false, false,
            List.of("No score could be calculated for this product."));
    }

    /** "price 0.72 · specs 0.64 · value 0.58 · brand 1.00" — used by the explanation service. */
    public String describeComponents() {
        StringBuilder out = new StringBuilder();
        for (ScoringCriterion criterion : ScoringCriterion.values()) {
            if (!components.containsKey(criterion)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(" · ");
            }
            out.append(criterion.label().toLowerCase()).append(' ')
                .append(String.format(java.util.Locale.ROOT, "%.2f", components.get(criterion)));
        }
        return out.toString();
    }
}
