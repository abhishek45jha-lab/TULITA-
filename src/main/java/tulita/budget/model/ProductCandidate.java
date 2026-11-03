package tulita.budget.model;

import tulita.budget.service.BudgetMoney;
import tulita.photocatalog.model.PhotoProduct;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One purchasable observation of a catalogue product: the curated variant plus one retailer offer.
 *
 * <p>A product that two retailers listed produces two candidates, because the store, the recorded
 * amount and the delivery assumption all differ. The candidate is the unit the optimiser chooses
 * from; it is never invented, it always points back at the original {@link PhotoProduct} and
 * {@link PhotoProduct.Offer}.</p>
 *
 * @param metrics normalised numeric specification values that were actually present, keyed by
 *                metric label (for example "Storage (GB)")
 */
public record ProductCandidate(
    String productId,
    String name,
    String brand,
    String model,
    String variant,
    String category,
    String store,
    String title,
    BigDecimal unitPrice,
    String currency,
    String url,
    String observedAt,
    String photoPath,
    Map<String, Double> metrics,
    int attributesPresent,
    int attributesTotal) {

    public ProductCandidate {
        metrics = Map.copyOf(metrics);
        unitPrice = BudgetMoney.scale(unitPrice);
    }

    public static ProductCandidate of(PhotoProduct product, PhotoProduct.Offer offer, String photoPath,
                                      Map<String, Double> metrics, int totalAttributes) {
        return new ProductCandidate(product.id(), product.name(), product.brand(), product.model(),
            product.variant(), product.category(), offer.store(), offer.title(), offer.price(),
            offer.currency(), offer.url(), offer.observedAt(), photoPath, metrics, metrics.size(),
            Math.max(metrics.size(), totalAttributes));
    }

    public BigDecimal lineTotal(int quantity) {
        return BudgetMoney.multiply(unitPrice, quantity);
    }

    /** 0..1 — how much of the category's specification set this candidate actually publishes. */
    public double attributeCoverage() {
        return attributesTotal == 0 ? 0.0 : (double) attributesPresent / attributesTotal;
    }

    /** Stable identity used to detect duplicate combinations. */
    public String signature() {
        return productId + "@" + store;
    }

    public String describeSpecs() {
        if (metrics.isEmpty()) {
            return "no comparable specification recorded";
        }
        StringBuilder out = new StringBuilder();
        metrics.forEach((key, value) -> {
            if (out.length() > 0) {
                out.append(" · ");
            }
            out.append(key).append(' ')
                .append(BigDecimal.valueOf(value).setScale(value >= 100 ? 0 : 1, BudgetMoney.ROUNDING)
                    .stripTrailingZeros().toPlainString());
        });
        return out.toString();
    }
}
