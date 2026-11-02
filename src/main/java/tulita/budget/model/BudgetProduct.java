package tulita.budget.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * One real, priced observation from the bundled historical INR catalogue.
 * It is a purchasable candidate only for planning; it is not a live offer.
 */
public record BudgetProduct(
        String recordId,
        String title,
        String brand,
        BudgetCategory category,
        String store,
        BigDecimal price,
        BigDecimal mrp,
        BigDecimal rating,
        String observedAt,
        String sourceUrl,
        String license,
        String dateNote,
        Map<String, String> specs,
        boolean renewed) {

    public BudgetProduct {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(price, "price");
        if (recordId.isBlank()) throw new IllegalArgumentException("A catalogue record needs an ID.");
        if (title.isBlank()) throw new IllegalArgumentException("A catalogue record needs a title.");
        if (price.signum() <= 0) throw new IllegalArgumentException("Recorded price must be positive.");
        title = title.strip();
        brand = brand == null || brand.isBlank() ? "Not stated" : brand.strip();
        observedAt = observedAt == null ? "" : observedAt.strip();
        sourceUrl = sourceUrl == null ? "" : sourceUrl.strip();
        license = license == null ? "" : license.strip();
        dateNote = dateNote == null ? "" : dateNote.strip();
        specs = Map.copyOf(specs == null ? Map.of() : specs);
    }

    public String observedLabel() {
        if (!observedAt.isBlank()) return observedAt.length() >= 10 ? observedAt.substring(0, 10) : observedAt;
        return dateNote.isBlank() ? "Date not supplied" : dateNote;
    }

    public String currency() {
        return "INR";
    }
}
