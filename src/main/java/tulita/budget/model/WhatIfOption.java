package tulita.budget.model;

/**
 * One pre-built "what if" variant of the current request, offered by the Explore Alternatives panel.
 *
 * <p>Each option carries the complete request it would optimise, so applying one is just
 * "put these values in the form and press Optimize" — no hidden state, and the visible form always
 * matches the recommendation that is on screen.</p>
 */
public record WhatIfOption(String label, String description, BudgetRequest request) {

    public String tooltip() {
        return label + " — " + description;
    }
}
