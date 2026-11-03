package tulita.budget.model;

import tulita.budget.service.BudgetMoney;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * One complete budget-planner request: the money available, what the shopper must or may buy, the
 * scoring preferences, and the (user supplied) charge assumptions.
 *
 * <p>This is a plain immutable value object. It performs only structural validation; the rules that
 * depend on the catalogue live in {@code OptimizationConstraintValidator}.</p>
 */
public record BudgetRequest(
    BigDecimal budget,
    String currency,
    List<CategoryRequirement> categories,
    PreferenceWeights weights,
    RecommendationMode mode,
    Map<String, BigDecimal> deliveryFeeByStore,
    BigDecimal discountPercent,
    BigDecimal discountMinimumSubtotal) {

    /** How strongly a category's score contributes to the overall recommendation score. */
    public enum Priority {
        LOW("Low", 0.6), MEDIUM("Medium", 1.0), HIGH("High", 1.4);

        private final String label;
        private final double factor;

        Priority(String label, double factor) {
            this.label = label;
            this.factor = factor;
        }

        public String label() {
            return label;
        }

        public double factor() {
            return factor;
        }

        public static Priority of(String label) {
            for (Priority p : values()) {
                if (p.label.equalsIgnoreCase(label) || p.name().equalsIgnoreCase(label)) {
                    return p;
                }
            }
            return MEDIUM;
        }
    }

    /** A minimum specification the shopper insists on, e.g. "Storage" at least 256. */
    public record MinimumSpec(String metric, double minimum) {
        public MinimumSpec {
            if (metric == null || metric.isBlank()) {
                throw new IllegalArgumentException("Choose a specification for the minimum requirement.");
            }
        }

        public String describe() {
            return metric + " >= " + BigDecimal.valueOf(minimum).stripTrailingZeros().toPlainString();
        }
    }

    /** One row of step 2: what to buy in a category, how many, and any preference for it. */
    public record CategoryRequirement(
        String category,
        boolean required,
        int minQuantity,
        int maxQuantity,
        String preferredBrand,
        Priority priority,
        BigDecimal targetUnitPrice,
        MinimumSpec minimumSpec) {

        public static final int MAX_UNITS = 99;

        public CategoryRequirement {
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("A category is required.");
            }
            if (minQuantity < 0) {
                throw new IllegalArgumentException("Minimum quantity for " + category + " cannot be negative.");
            }
            if (maxQuantity < minQuantity) {
                throw new IllegalArgumentException(
                    "Maximum quantity for " + category + " must be at least the minimum.");
            }
            if (required && minQuantity < 1) {
                throw new IllegalArgumentException(
                    "Required category " + category + " must ask for at least one unit.");
            }
            if (maxQuantity > MAX_UNITS) {
                throw new IllegalArgumentException(
                    "Maximum quantity for " + category + " is limited to " + MAX_UNITS + " units.");
            }
            priority = priority == null ? Priority.MEDIUM : priority;
            preferredBrand = preferredBrand == null || preferredBrand.isBlank() ? null : preferredBrand.trim();
            if (targetUnitPrice != null && targetUnitPrice.signum() < 0) {
                throw new IllegalArgumentException("Target price cannot be negative.");
            }
        }

        public static CategoryRequirement required(String category, int quantity) {
            return new CategoryRequirement(category, true, quantity, Math.max(1, quantity), null,
                Priority.MEDIUM, null, null);
        }

        public static CategoryRequirement optional(String category) {
            return new CategoryRequirement(category, false, 0, 1, null, Priority.LOW, null, null);
        }

        /** Lowest quantity the engine must place in this category (0 for an optional category). */
        public int effectiveMin() {
            return Math.max(required ? 1 : 0, minQuantity);
        }

        public int effectiveMax() {
            return Math.max(effectiveMin(), Math.min(MAX_UNITS, maxQuantity));
        }

        public CategoryRequirement withQuantities(int min, int max) {
            return new CategoryRequirement(category, required, min, max, preferredBrand, priority,
                targetUnitPrice, minimumSpec);
        }
    }

    public BudgetRequest {
        categoryCurrencyCheck(currency);
        budget = BudgetMoney.scale(budget);
        if (budget.signum() < 0) {
            throw new IllegalArgumentException("The budget cannot be negative.");
        }
        categories = List.copyOf(new LinkedHashSet<>(categories));
        deliveryFeeByStore = Map.copyOf(deliveryFeeByStore == null ? Map.of() : deliveryFeeByStore);
        discountPercent = BudgetMoney.scale(discountPercent == null ? BudgetMoney.ZERO : discountPercent);
        discountMinimumSubtotal =
            BudgetMoney.scale(discountMinimumSubtotal == null ? BudgetMoney.ZERO : discountMinimumSubtotal);
    }

    private static void categoryCurrencyCheck(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("The catalogue currency is unknown.");
        }
    }

    public static BudgetRequest of(BigDecimal budget, String currency, List<CategoryRequirement> categories,
                                   PreferenceWeights weights, RecommendationMode mode) {
        return new BudgetRequest(budget, currency, categories, weights, mode, Map.of(),
            BudgetMoney.ZERO, BudgetMoney.ZERO);
    }

    public BudgetRequest withMode(RecommendationMode newMode) {
        return new BudgetRequest(budget, currency, categories, weights, newMode, deliveryFeeByStore,
            discountPercent, discountMinimumSubtotal);
    }

    public BudgetRequest withWeights(PreferenceWeights newWeights) {
        return new BudgetRequest(budget, currency, categories, newWeights, mode, deliveryFeeByStore,
            discountPercent, discountMinimumSubtotal);
    }

    public BudgetRequest withBudget(BigDecimal newBudget) {
        return new BudgetRequest(newBudget, currency, categories, weights, mode, deliveryFeeByStore,
            discountPercent, discountMinimumSubtotal);
    }

    public BudgetRequest withCategories(List<CategoryRequirement> newCategories) {
        return new BudgetRequest(budget, currency, newCategories, weights, mode, deliveryFeeByStore,
            discountPercent, discountMinimumSubtotal);
    }

    public BudgetRequest withCharges(Map<String, BigDecimal> fees, BigDecimal discount, BigDecimal minimum) {
        return new BudgetRequest(budget, currency, categories, weights, mode, fees, discount, minimum);
    }

    /** Structural validation. Returns every problem found, never just the first one. */
    public List<String> structuralErrors() {
        List<String> errors = new ArrayList<>();
        if (budget.signum() <= 0) {
            errors.add("The budget must be greater than zero.");
        }
        if (budget.scale() > BudgetMoney.SCALE) {
            errors.add("The budget cannot have more than two decimal places.");
        }
        if (categories.isEmpty()) {
            errors.add("Select at least one product category.");
        }
        List<String> seen = new ArrayList<>();
        for (CategoryRequirement c : categories) {
            if (seen.contains(c.category())) {
                errors.add("Category '" + c.category() + "' appears twice.");
            }
            seen.add(c.category());
            if (c.maxQuantity() < 1) {
                errors.add("Category '" + c.category() + "' must allow at least one unit.");
            }
            if (!c.required() && c.minQuantity() > 0 && c.maxQuantity() < c.minQuantity()) {
                errors.add("Category '" + c.category() + "' has an impossible quantity range.");
            }
        }
        if (categories.stream().noneMatch(CategoryRequirement::required)) {
            errors.add("Mark at least one category as required, otherwise there is nothing to optimise.");
        }
        if (discountPercent.signum() < 0 || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add("The discount percentage must be between 0 and 100.");
        }
        for (Map.Entry<String, BigDecimal> fee : deliveryFeeByStore.entrySet()) {
            if (fee.getValue() == null || fee.getValue().signum() < 0) {
                errors.add("The delivery charge for " + fee.getKey() + " cannot be negative.");
            }
        }
        if (weights.total() <= 0) {
            errors.add("At least one preference weight must be greater than zero.");
        }
        return errors;
    }

    public List<CategoryRequirement> requiredCategories() {
        return categories.stream().filter(CategoryRequirement::required).toList();
    }

    public Map<String, CategoryRequirement> byCategory() {
        Map<String, CategoryRequirement> out = new LinkedHashMap<>();
        for (CategoryRequirement c : categories) {
            out.put(c.category(), c);
        }
        return out;
    }

    public BigDecimal feeFor(String store) {
        return BudgetMoney.scale(deliveryFeeByStore.getOrDefault(store, BudgetMoney.ZERO));
    }

    public boolean hasCharges() {
        return deliveryFeeByStore.values().stream().anyMatch(v -> v != null && v.signum() > 0)
            || discountPercent.signum() > 0;
    }
}
