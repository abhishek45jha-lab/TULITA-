package tulita.budget.model;

import tulita.budget.service.BudgetMoney;

import java.math.BigDecimal;

/**
 * The share of the charges that belongs to one line of the basket.
 *
 * <p>Delivery is charged once per retailer but the card for each product must still show what that
 * product actually costs, so the fee is split across the lines bought from that shop. The discount is
 * split across every line in proportion to its item total. Both splits are exact: the last cent of any
 * rounding remainder goes to the largest line, so the sum of {@code finalCost} over all lines equals the
 * basket total exactly.</p>
 */
public record LineCharges(BigDecimal itemTotal, BigDecimal delivery, BigDecimal discount,
                          BigDecimal finalCost) {

    public LineCharges {
        itemTotal = BudgetMoney.scale(itemTotal);
        delivery = BudgetMoney.scale(delivery);
        discount = BudgetMoney.scale(discount);
        finalCost = BudgetMoney.scale(finalCost);
    }

    public boolean hasCharges() {
        return delivery.signum() != 0 || discount.signum() != 0;
    }

    /** "item USD 49.95 · delivery USD 2.50 · discount −USD 1.00" — empty when nothing was charged. */
    /** One short label per amount, so a card can show them on separate lines without splitting a number. */
    public java.util.List<String> describeLines(String currency) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("item " + BudgetMoney.format(itemTotal, currency));
        if (delivery.signum() != 0) {
            lines.add("delivery " + BudgetMoney.format(delivery, currency));
        }
        if (discount.signum() != 0) {
            lines.add("discount \u2212 " + BudgetMoney.format(discount, currency));
        }
        lines.add("final " + BudgetMoney.format(finalCost, currency));
        return java.util.List.copyOf(lines);
    }

    public String describe(String currency) {
        if (!hasCharges()) {
            return "";
        }
        StringBuilder out = new StringBuilder("item " + BudgetMoney.format(itemTotal, currency));
        if (delivery.signum() != 0) {
            out.append(" · delivery ").append(BudgetMoney.format(delivery, currency));
        }
        if (discount.signum() != 0) {
            out.append(" · discount \u2212 ").append(BudgetMoney.format(discount, currency));
        }
        out.append(" · final ").append(BudgetMoney.format(finalCost, currency));
        return out.toString();
    }
}
