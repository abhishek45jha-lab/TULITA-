package tulita.budget.model;

/** The two explainable recommendation objectives. */
public enum PlanMode {
    BEST_VALUE("Best Value"),
    LOWEST_COST("Lowest Cost");

    private final String label;

    PlanMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
