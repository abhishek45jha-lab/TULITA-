package tulita.budget.model;

/**
 * The criteria a recommendation score is built from.
 *
 * <p>Every criterion is normalised to 0.0 - 1.0 before weighting, so the weighted sum stays
 * comparable between products and between categories.</p>
 */
public enum ScoringCriterion {
    PRICE("Price", "lower recorded price scores higher"),
    SPECIFICATIONS("Specifications", "how well the recorded specifications compare inside the category"),
    VALUE("Value for money", "specification suitability per unit of price"),
    BRAND("Brand preference", "matches the brand you asked for"),
    WARRANTY("Warranty suitability", "warranty length, only when the catalogue records one");

    private final String label;
    private final String description;

    ScoringCriterion(String label, String description) {
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
