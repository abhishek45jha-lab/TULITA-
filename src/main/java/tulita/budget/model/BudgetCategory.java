package tulita.budget.model;

/** The four budget-planning categories supported by the bundled INR electronics catalogue. */
public enum BudgetCategory {
    LAPTOPS("Laptops", "Laptop"),
    KEYBOARDS("Keyboards", "Keyboard"),
    MICE("Mice", "Mouse"),
    HEADPHONES("Headphones", "Headphone");

    private final String label;
    private final String singular;

    BudgetCategory(String label, String singular) {
        this.label = label;
        this.singular = singular;
    }

    public String label() {
        return label;
    }

    public String singular() {
        return singular;
    }
}
