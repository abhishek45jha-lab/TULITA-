package tulita.budget.model;

/** The four optimisation modes offered by the budget planner. */
public enum RecommendationMode {
    BEST_VALUE("Best Value",
        "Maximises the weighted TULITA Recommendation Score inside your budget."),
    LOWEST_COST("Lowest Cost",
        "Minimises the total cost while still satisfying every mandatory requirement."),
    BEST_SPECS("Best Specifications",
        "Maximises specification suitability first, then cost."),
    BALANCED("Balanced",
        "Balances recommendation score against how much of the budget is left unspent.");

    private final String label;
    private final String description;

    RecommendationMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
