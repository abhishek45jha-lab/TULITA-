package tulita.budget.service;

import tulita.budget.model.BudgetBreakdown;
import tulita.budget.model.LineCharges;
import tulita.budget.model.BudgetRequest;
import tulita.budget.model.SelectedProduct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All the arithmetic of the budget planner lives here, so the optimiser and the screen can never
 * disagree about a total.
 *
 * <p>total = items + delivery - discount, with delivery charged once per retailer and the discount
 * applied only when its threshold is met. Every step is {@link BigDecimal} at scale 2 with HALF_UP
 * rounding; the optimiser additionally works in integer cents derived from the same methods.</p>
 */
public final class BudgetCalculationService {

    /** Result of the money calculation for one combination of lines. */
    public record Costs(BigDecimal itemsSubtotal, BigDecimal deliveryCharges, BigDecimal discount,
                        BigDecimal total, Map<String, BigDecimal> deliveryByStore,
                        Map<String, BigDecimal> spendByCategory, List<String> notes) {

        public Costs {
            deliveryByStore = Map.copyOf(deliveryByStore);
            spendByCategory = Map.copyOf(spendByCategory);
            notes = List.copyOf(notes);
        }
    }

    public Costs costs(BudgetRequest request, List<SelectedProduct> selected) {
        BigDecimal items = BudgetMoney.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Set<String> stores = new LinkedHashSet<>();
        for (SelectedProduct line : selected) {
            items = items.add(line.lineTotal());
            stores.add(line.candidate().store());
            byCategory.merge(line.candidate().category(), line.lineTotal(), BigDecimal::add);
        }
        items = BudgetMoney.scale(items);

        Map<String, BigDecimal> deliveryByStore = new LinkedHashMap<>();
        BigDecimal delivery = BudgetMoney.ZERO;
        for (String store : stores) {
            BigDecimal fee = request.feeFor(store);
            if (fee.signum() > 0) {
                deliveryByStore.put(store, fee);
                delivery = delivery.add(fee);
            }
        }
        delivery = BudgetMoney.scale(delivery);

        List<String> notes = new ArrayList<>();
        BigDecimal discount = discountFor(request, items, notes);

        BigDecimal total = BudgetMoney.scale(items.add(delivery).subtract(discount));
        return new Costs(items, delivery, discount, total, deliveryByStore, byCategory, notes);
    }

    /** The discount actually payable for this item subtotal, plus a note explaining why (not) applied. */
    public BigDecimal discountFor(BudgetRequest request, BigDecimal itemsSubtotal, List<String> notes) {
        BigDecimal percent = request.discountPercent();
        if (percent == null || percent.signum() <= 0) {
            return BudgetMoney.ZERO;
        }
        BigDecimal minimum = request.discountMinimumSubtotal();
        boolean thresholdMet = minimum == null || minimum.signum() <= 0
            || itemsSubtotal.compareTo(minimum) >= 0;
        if (!thresholdMet) {
            if (notes != null) {
                notes.add("Your " + BudgetMoney.percent(percent.doubleValue())
                    + " discount needs an item subtotal of at least "
                    + BudgetMoney.format(minimum, request.currency()) + "; this basket is below it, so no"
                    + " discount was applied.");
            }
            return BudgetMoney.ZERO;
        }
        BigDecimal discount = BudgetMoney.percentOf(itemsSubtotal, percent);
        if (notes != null && discount.signum() > 0) {
            notes.add("Discount applied: " + BudgetMoney.percent(percent.doubleValue()) + " of the item subtotal = "
                + BudgetMoney.format(discount, request.currency()) + " (your own assumption, not retailer data).");
        }
        return discount;
    }

    /**
     * Splits the basket charges across its lines.
     *
     * <p>Delivery is apportioned among the lines bought from the same retailer, in proportion to their
     * item totals; the discount is apportioned across all lines the same way. Any rounding remainder is
     * given to the largest line, so {@code sum(finalCost) == basket total} exactly (tested).</p>
     */
    public Map<String, LineCharges> allocate(BudgetRequest request, List<SelectedProduct> selected) {
        Costs costs = costs(request, selected);
        Map<String, BigDecimal> deliveryPart = new LinkedHashMap<>();
        Map<String, BigDecimal> discountPart = new LinkedHashMap<>();
        for (String store : costs.deliveryByStore().keySet()) {
            List<SelectedProduct> group = selected.stream()
                .filter(line -> line.candidate().store().equals(store)).toList();
            deliveryPart.putAll(share(costs.deliveryByStore().get(store), group));
        }
        discountPart.putAll(share(costs.discount(), selected));

        Map<String, LineCharges> out = new LinkedHashMap<>();
        for (SelectedProduct line : selected) {
            String key = line.candidate().signature();
            BigDecimal item = line.lineTotal();
            BigDecimal delivery = deliveryPart.getOrDefault(key, BudgetMoney.ZERO);
            BigDecimal discount = discountPart.getOrDefault(key, BudgetMoney.ZERO);
            out.put(key, new LineCharges(item, delivery, discount, item.add(delivery).subtract(discount)));
        }
        return out;
    }

    /** Proportional split of an amount over a group of lines, remainder to the largest line. */
    private Map<String, BigDecimal> share(BigDecimal amount, List<SelectedProduct> group) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (amount == null || amount.signum() == 0 || group.isEmpty()) {
            return out;
        }
        BigDecimal groupTotal = group.stream().map(SelectedProduct::lineTotal)
            .reduce(BudgetMoney.ZERO, BigDecimal::add);
        if (groupTotal.signum() <= 0) {
            return out;
        }
        BigDecimal allocated = BudgetMoney.ZERO;
        SelectedProduct largest = group.stream()
            .max(Comparator.comparing(SelectedProduct::lineTotal)).orElse(group.get(0));
        for (SelectedProduct line : group) {
            if (line == largest) {
                continue;
            }
            BigDecimal part = amount.multiply(line.lineTotal())
                .divide(groupTotal, BudgetMoney.SCALE, java.math.RoundingMode.DOWN);
            out.put(line.candidate().signature(), part);
            allocated = allocated.add(part);
        }
        out.put(largest.candidate().signature(), BudgetMoney.scale(amount.subtract(allocated)));
        return out;
    }

    /**
     * Total cost of a partial or complete basket in integer cents.
     *
     * <p>Used by the search, which keeps everything in integer units so the budget test is exact. The
     * discount threshold is honoured exactly as {@link #costs} does it.</p>
     */
    public long totalCents(BudgetRequest request, long itemsSubtotalCents, Set<String> stores,
                           Map<String, Long> deliveryCentsByStore) {
        long delivery = 0;
        for (String store : stores) {
            delivery += deliveryCentsByStore.getOrDefault(store, 0L);
        }
        BigDecimal subtotal = BudgetMoney.fromCents(itemsSubtotalCents);
        BigDecimal discount = discountFor(request, subtotal, null);
        long discountCents = BudgetMoney.cents(discount);
        return itemsSubtotalCents + delivery - discountCents;
    }

    /**
     * A lower bound of the final cost of a partial basket.
     *
     * <p>The search prunes a state only when even this optimistic bound exceeds the budget, so pruning
     * can never remove a combination that would have fitted afterwards.</p>
     */
    public long lowerBoundCents(BudgetRequest request, long itemsSubtotalCents, Set<String> stores,
                                Map<String, Long> deliveryCentsByStore) {
        long delivery = 0;
        for (String store : stores) {
            delivery += deliveryCentsByStore.getOrDefault(store, 0L);
        }
        return itemsSubtotalCents + delivery - BudgetMoney.cents(maxDiscountCents(request, itemsSubtotalCents));
    }

    private BigDecimal maxDiscountCents(BudgetRequest request, long itemsSubtotalCents) {
        BigDecimal percent = request.discountPercent();
        if (percent == null || percent.signum() <= 0) {
            return BudgetMoney.ZERO;
        }
        return BudgetMoney.percentOf(BudgetMoney.fromCents(itemsSubtotalCents), percent);
    }

    /** Completes a breakdown from the money side plus the request. */
    public BudgetBreakdown breakdown(BudgetRequest request, Costs costs) {
        BigDecimal remaining = BudgetMoney.scale(request.budget().subtract(costs.total()));
        double utilization = request.budget().signum() == 0
            ? 0.0
            : BudgetMoney.clamp(costs.total().doubleValue() / request.budget().doubleValue() * 100.0,
            0.0, 1000.0);
        List<String> notes = new ArrayList<>(costs.notes());
        if (!request.hasCharges()) {
            notes.add("No delivery charge or discount was entered, so the total is the item subtotal only."
                + " The catalogue records no shipping or promotion data.");
        } else {
            notes.add("Delivery charges and discounts are your own assumptions; they are not recorded"
                + " retailer data.");
        }
        return new BudgetBreakdown(request.budget(), costs.itemsSubtotal(), costs.deliveryCharges(),
            costs.discount(), costs.total(), remaining, utilization, costs.spendByCategory(),
            costs.deliveryByStore(), request.currency(), notes);
    }
}
