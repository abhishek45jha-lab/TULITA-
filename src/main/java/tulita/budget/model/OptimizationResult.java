package tulita.budget.model;

import java.util.List;

/**
 * Outcome of one optimise request.
 *
 * @param feasible        true when a combination satisfying every mandatory requirement was found
 * @param exhaustiveSearch true when the search explored the whole Pareto front without hitting the
 *                        state cap; false means the result is a documented heuristic
 * @param statesEvaluated number of surviving search states, reported for transparency
 */
public record OptimizationResult(
    boolean feasible,
    BudgetRecommendation primary,
    List<RecommendationAlternative> alternatives,
    List<String> infeasibleReasons,
    List<String> suggestions,
    List<String> limitations,
    boolean exhaustiveSearch,
    int statesEvaluated,
    String algorithmNote) {

    public OptimizationResult {
        alternatives = List.copyOf(alternatives);
        infeasibleReasons = List.copyOf(infeasibleReasons);
        suggestions = List.copyOf(suggestions);
        limitations = List.copyOf(limitations);
    }

    public static OptimizationResult infeasible(List<String> reasons, List<String> suggestions,
                                                List<String> limitations, String algorithmNote) {
        return new OptimizationResult(false, null, List.of(), reasons, suggestions, limitations, true, 0,
            algorithmNote);
    }

    public boolean hasAlternatives() {
        return !alternatives.isEmpty();
    }
}
