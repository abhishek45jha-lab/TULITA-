package tulita.budget.model;

import java.util.List;

/**
 * A second, genuinely different combination produced by a different objective.
 *
 * <p>{@code signature} is the ordered list of chosen candidates and quantities. The service uses it
 * to drop alternatives that are identical to the primary recommendation, so the same basket is never
 * presented twice under two names.</p>
 */
public record RecommendationAlternative(
    RecommendationMode mode,
    String label,
    BudgetRecommendation recommendation,
    String signature,
    List<String> differences,
    List<String> advantages,
    List<String> disadvantages) {

    public RecommendationAlternative {
        differences = List.copyOf(differences);
        advantages = List.copyOf(advantages);
        disadvantages = List.copyOf(disadvantages);
    }

    public String totalText() {
        return recommendation.breakdown().formattedTotal();
    }

    public String remainingText() {
        return recommendation.breakdown().formattedRemaining();
    }
}
