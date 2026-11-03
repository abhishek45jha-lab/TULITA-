package tulita.budget.engine;

import tulita.budget.model.BudgetRequest;
import tulita.budget.model.ProductScore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a budget request and then re-validates the recommendation the engine produced.
 *
 * <p>The second job matters as much as the first: the planner refuses to display a basket that fails
 * any mandatory rule, even if the search produced it. A failed self-check is a programming error, so
 * it is raised instead of being displayed.</p>
 */
public final class OptimizationConstraintValidator {

    /** Thrown when a request cannot be evaluated at all. */
    public static final class InvalidRequestException extends IllegalArgumentException {
        private final List<String> problems;

        public InvalidRequestException(List<String> problems) {
            super(String.join(" ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> problems() {
            return problems;
        }
    }

    public void validateRequest(BudgetRequest request) {
        if (request == null) {
            throw new InvalidRequestException(List.of("No budget request was supplied."));
        }
        List<String> problems = new ArrayList<>(request.structuralErrors());
        if (problems.isEmpty()) {
            for (BudgetRequest.CategoryRequirement requirement : request.categories()) {
                if (requirement.minimumSpec() != null
                    && tulita.budget.service.CategorySpecProfiles.forCategory(requirement.category())
                    .byLabel(requirement.minimumSpec().metric()) == null) {
                    problems.add("The minimum specification '" + requirement.minimumSpec().metric()
                        + "' is not measured in category '" + requirement.category() + "'.");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidRequestException(problems);
        }
    }

    /**
     * Re-checks a finished recommendation against every hard rule.
     *
     * @return the list of violations; empty means the basket is valid
     */
    public List<String> violations(BudgetRequest request, List<tulita.budget.model.SelectedProduct> selected,
                                   java.math.BigDecimal total, Set<String> catalogueCategories) {
        List<String> problems = new ArrayList<>();
        if (selected == null || selected.isEmpty()) {
            problems.add("The recommendation is empty.");
            return problems;
        }
        if (total.compareTo(request.budget()) > 0) {
            problems.add("The total " + total + " exceeds the budget " + request.budget() + ".");
        }
        Set<String> present = new LinkedHashSet<>();
        Set<String> stores = new LinkedHashSet<>();
        for (tulita.budget.model.SelectedProduct line : selected) {
            BudgetRequest.CategoryRequirement requirement = request.byCategory().get(line.candidate().category());
            if (requirement == null) {
                problems.add("Category '" + line.candidate().category() + "' was not requested.");
                continue;
            }
            if (catalogueCategories != null && !catalogueCategories.contains(line.candidate().category())) {
                problems.add("Category '" + line.candidate().category() + "' is not in the catalogue.");
            }
            if (line.quantity() < requirement.effectiveMin() || line.quantity() > requirement.effectiveMax()) {
                problems.add("Quantity " + line.quantity() + " for '" + line.candidate().category()
                    + "' is outside the allowed range " + requirement.effectiveMin() + "-"
                    + requirement.effectiveMax() + ".");
            }
            if (line.candidate().unitPrice() == null || line.candidate().unitPrice().signum() < 0) {
                problems.add("Product '" + line.candidate().productId() + "' has no usable price.");
            }
            if (requirement.minimumSpec() != null) {
                Double value = line.candidate().metrics().get(requirement.minimumSpec().metric());
                if (value == null || value + 1e-9 < requirement.minimumSpec().minimum()) {
                    problems.add("Product '" + line.candidate().productId()
                        + "' does not meet the minimum specification "
                        + requirement.minimumSpec().describe() + ".");
                }
            }
            present.add(line.candidate().category());
            stores.add(line.candidate().store());
        }
        for (BudgetRequest.CategoryRequirement requirement : request.requiredCategories()) {
            if (!present.contains(requirement.category())) {
                problems.add("Required category '" + requirement.category() + "' is missing.");
            }
        }
        if (stores.size() < 1) {
            problems.add("The recommendation has no retailer.");
        }
        return problems;
    }

    /** True when the candidate publishes the metric and meets the minimum. */
    public boolean meetsMinimumSpec(ProductScore score, BudgetRequest.CategoryRequirement requirement) {
        if (requirement == null || requirement.minimumSpec() == null) {
            return true;
        }
        Double value = score.candidate().metrics().get(requirement.minimumSpec().metric());
        return value != null && value + 1e-9 >= requirement.minimumSpec().minimum();
    }
}
