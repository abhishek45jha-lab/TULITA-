package tulita.budget.model;

import java.util.Objects;

/** A selected category, whether the user can drop it, and how many units are needed. */
public record CategoryRequirement(BudgetCategory category, boolean required, int quantity) {
    public static final int MAX_QUANTITY = 3;

    public CategoryRequirement {
        Objects.requireNonNull(category, "category");
        if (quantity < 1 || quantity > MAX_QUANTITY)
            throw new IllegalArgumentException("Quantity for " + category.label() + " must be between 1 and " + MAX_QUANTITY + ".");
    }
}
