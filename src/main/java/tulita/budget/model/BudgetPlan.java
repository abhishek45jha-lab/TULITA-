package tulita.budget.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The complete result of one optimization: basket, totals, explanations and alternatives. */
public record BudgetPlan(
        PlanMode mode,
        BigDecimal budget,
        List<BudgetSelection> selections,
        BigDecimal totalCost,
        BigDecimal remaining,
        BigDecimal utilizationPercent,
        Map<BudgetCategory, BigDecimal> categorySpend,
        boolean feasible,
        boolean optimal,
        int utility,
        List<String> warnings,
        List<String> explanations,
        List<BudgetAlternative> alternatives) {

    public BudgetPlan {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(totalCost, "totalCost");
        Objects.requireNonNull(remaining, "remaining");
        Objects.requireNonNull(utilizationPercent, "utilizationPercent");
        selections = List.copyOf(selections == null ? List.of() : selections);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        explanations = List.copyOf(explanations == null ? List.of() : explanations);
        alternatives = List.copyOf(alternatives == null ? List.of() : alternatives);
        categorySpend = Map.copyOf(categorySpend == null ? Map.of() : categorySpend);
    }

    public int itemCount() {
        return selections.stream().mapToInt(BudgetSelection::quantity).sum();
    }

    public int lineCount() {
        return selections.size();
    }

    public static BudgetPlan infeasible(PlanMode mode, BigDecimal budget, List<String> warnings) {
        return new BudgetPlan(mode, money(budget), List.of(), BigDecimal.ZERO.setScale(2), money(budget),
                BigDecimal.ZERO.setScale(1), new EnumMap<>(BudgetCategory.class), false, false, 0,
                warnings, List.of(), List.of());
    }

    public static BudgetPlan fromAlternative(BudgetAlternative alternative, BigDecimal budget,
                                             List<String> warnings, List<String> explanations) {
        List<BudgetSelection> selected = alternative.selections();
        BigDecimal total = alternative.totalCost();
        Map<BudgetCategory, BigDecimal> spend = new EnumMap<>(BudgetCategory.class);
        for (BudgetSelection selection : selected) {
            spend.merge(selection.product().category(), selection.lineTotal(), BigDecimal::add);
        }
        List<String> notes = new ArrayList<>();
        notes.add("Selected in " + alternative.mode().label() + " mode; applied alternative plan without changing the input data.");
        notes.add("Every price is a recorded catalogue value; no discount, delivery or current-availability claim is made.");
        if (explanations != null) {
            for (String explanation : explanations) {
                if (!explanation.startsWith("Total ") && !explanation.startsWith("Selected in ")) notes.add(explanation);
            }
        }
        notes.add("Applied alternative: total " + tulita.util.Money.inr(total) + " of "
                + tulita.util.Money.inr(budget) + " · remaining " + tulita.util.Money.inr(budget.subtract(total))
                + " · utilisation " + utilization(total, budget).toPlainString() + "%.");
        return new BudgetPlan(alternative.mode(), money(budget), selected, money(total), money(budget.subtract(total)),
                utilization(total, budget), spend, true, false, alternative.utility(), warnings, notes,
                List.of());
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal utilization(BigDecimal total, BigDecimal budget) {
        if (budget == null || budget.signum() <= 0) return BigDecimal.ZERO.setScale(1);
        return total.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP);
    }
}
