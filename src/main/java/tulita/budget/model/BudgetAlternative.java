package tulita.budget.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** A complete, feasible alternative basket the user can apply with one click. */
public record BudgetAlternative(
        String label,
        String description,
        PlanMode mode,
        List<BudgetSelection> selections,
        BigDecimal totalCost,
        int utility,
        BigDecimal deltaCost,
        int deltaUtility) {

    public BudgetAlternative {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(totalCost, "totalCost");
        Objects.requireNonNull(deltaCost, "deltaCost");
        selections = List.copyOf(selections == null ? List.of() : selections);
    }

    public int itemCount() {
        return selections.stream().mapToInt(BudgetSelection::quantity).sum();
    }

    public int lineCount() {
        return selections.size();
    }
}
