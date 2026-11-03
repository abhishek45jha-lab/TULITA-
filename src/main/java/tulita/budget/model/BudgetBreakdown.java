package tulita.budget.model;

import tulita.budget.service.BudgetMoney;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The money side of a recommendation.
 *
 * <p>total = item subtotal + delivery charges - eligible discount. Delivery is charged once per
 * retailer that appears in the combination, so two products bought from the same shop never pay the
 * fee twice. The discount is applied only when the item subtotal reaches its threshold.</p>
 */
public record BudgetBreakdown(
    BigDecimal budget,
    BigDecimal itemsSubtotal,
    BigDecimal deliveryCharges,
    BigDecimal discount,
    BigDecimal total,
    BigDecimal remaining,
    double utilizationPercent,
    Map<String, BigDecimal> spendByCategory,
    Map<String, BigDecimal> deliveryByStore,
    String currency,
    List<String> notes) {

    public BudgetBreakdown {
        budget = BudgetMoney.scale(budget);
        itemsSubtotal = BudgetMoney.scale(itemsSubtotal);
        deliveryCharges = BudgetMoney.scale(deliveryCharges);
        discount = BudgetMoney.scale(discount);
        total = BudgetMoney.scale(total);
        remaining = BudgetMoney.scale(remaining);
        spendByCategory = Map.copyOf(spendByCategory);
        deliveryByStore = Map.copyOf(deliveryByStore);
        notes = List.copyOf(notes);
    }

    public boolean withinBudget() {
        return total.compareTo(budget) <= 0;
    }

    public String formattedTotal() {
        return BudgetMoney.format(total, currency);
    }

    public String formattedRemaining() {
        return BudgetMoney.format(remaining, currency);
    }

    /** Head-room in percent of the budget, never negative. */
    public double headroomPercent() {
        if (budget.signum() == 0) {
            return 0.0;
        }
        return BudgetMoney.clamp(100.0 - utilizationPercent, 0.0, 100.0);
    }

    public Map<String, String> asText() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("budget", BudgetMoney.format(budget, currency));
        out.put("itemsSubtotal", BudgetMoney.format(itemsSubtotal, currency));
        out.put("deliveryCharges", BudgetMoney.format(deliveryCharges, currency));
        out.put("discount", BudgetMoney.format(discount, currency));
        out.put("total", BudgetMoney.format(total, currency));
        out.put("remaining", BudgetMoney.format(remaining, currency));
        out.put("utilizationPercent", BudgetMoney.percent(utilizationPercent));
        return out;
    }
}
