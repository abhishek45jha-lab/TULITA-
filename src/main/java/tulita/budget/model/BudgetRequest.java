package tulita.budget.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Immutable user intent for one optimization run. */
public record BudgetRequest(
        BigDecimal budget,
        List<CategoryRequirement> requirements,
        PlanMode mode,
        Set<String> preferredBrands) {

    public BudgetRequest {
        requirements = List.copyOf(requirements == null ? List.of() : requirements);
        preferredBrands = Set.copyOf(preferredBrands == null ? Set.of() : preferredBrands);
    }

    public CategoryRequirement requirement(BudgetCategory category) {
        return requirements.stream().filter(r -> r.category() == category).findFirst().orElse(null);
    }
}
