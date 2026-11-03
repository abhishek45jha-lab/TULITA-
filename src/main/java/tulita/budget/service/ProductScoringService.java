package tulita.budget.service;

import tulita.budget.model.BudgetRequest;
import tulita.budget.model.PreferenceWeights;
import tulita.budget.model.ProductCandidate;
import tulita.budget.model.ProductScore;
import tulita.budget.model.ScoringCriterion;
import tulita.photocatalog.model.PhotoProduct;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns catalogue records into scored candidates.
 *
 * <p>Score = sum over available criteria of (normalised weight x normalised component). Components:</p>
 * <ul>
 *   <li><b>price</b> — min-max inverted inside the category, or "target / price" capped at 1 when the
 *       shopper supplied a target price for that category;</li>
 *   <li><b>specifications</b> — 0.7 x mean of the normalised present metrics + 0.3 x attribute
 *       coverage, so a product that publishes three of five metrics cannot outrank an equally strong
 *       product that publishes all five;</li>
 *   <li><b>value</b> — specification score per unit of price, min-max normalised inside the
 *       category;</li>
 *   <li><b>brand</b> — 1.0 on a match, 0.0 otherwise (dropped when no brand was requested);</li>
 *   <li><b>warranty</b> — normalised warranty length (dropped when the category records none).</li>
 * </ul>
 *
 * <p>Missing specification, value or warranty data scores the neutral 0.5, never 1.0, and the note
 * list says so. Criteria that no candidate in the category can support are dropped from the weighting
 * and the remaining weights are renormalised, so an unavailable criterion cannot distort the ranking.</p>
 */
public final class ProductScoringService {

    /** Neutral value used when a criterion exists in principle but this product does not publish it. */
    public static final double NEUTRAL = 0.5;

    /** Specification score, plus the notes that explain what could not be scored. */
    public record ScoringOutcome(List<ProductScore> scores, List<String> limitations) {
        public ScoringOutcome {
            scores = List.copyOf(scores);
            limitations = List.copyOf(limitations);
        }

        public List<ProductScore> forCategory(String category) {
            return scores.stream().filter(s -> s.candidate().category().equals(category)).toList();
        }
    }

    /**
     * Builds one candidate per (product, retailer offer) for the requested categories.
     *
     * <p>The extracted specification metrics are merged across all listings of the same curated variant
     * before scoring. The catalogue states that both offers are the same physical product, so the two
     * retailers cannot genuinely differ in RAM or capacity; if one shop simply documents more keys, that
     * must not make its own price look better. Price, store and delivery remain per offer.</p>
     */
    public List<ProductCandidate> candidates(List<PhotoProduct> products, Set<String> categories) {
        List<ProductCandidate> out = new ArrayList<>();
        for (PhotoProduct product : products) {
            if (!categories.contains(product.category())) {
                continue;
            }
            CategorySpecProfiles.Profile profile = CategorySpecProfiles.forCategory(product.category());
            String photoPath = product.photos().isEmpty() ? null : product.photos().get(0).path();
            List<PhotoProduct.Offer> priced = new ArrayList<>();
            Map<String, Double> merged = new LinkedHashMap<>();
            // pass 1: merge the specification evidence of every listing of this variant
            for (PhotoProduct.Offer offer : product.offers()) {
                if (offer.price() == null || offer.price().signum() < 0) {
                    continue; // a candidate without a usable price can never enter a cost-constrained basket
                }
                priced.add(offer);
                profile.extract(offer.specifications()).forEach(merged::putIfAbsent);
            }
            // pass 2: build candidates only after the merge, so every listing carries the same profile
            Map<String, Double> evidence = Map.copyOf(merged);
            for (PhotoProduct.Offer offer : priced) {
                out.add(ProductCandidate.of(product, offer, photoPath, evidence, profile.metrics().size()));
            }
        }
        out.sort(Comparator.comparing(ProductCandidate::category)
            .thenComparing(ProductCandidate::productId)
            .thenComparing(ProductCandidate::store));
        return out;
    }

    /** Scores every candidate. Normalisation happens inside each category. */
    public ScoringOutcome scoreAll(List<ProductCandidate> candidates, BudgetRequest request) {
        List<String> limitations = new ArrayList<>();
        Map<String, List<ProductCandidate>> byCategory = new LinkedHashMap<>();
        for (ProductCandidate candidate : candidates) {
            byCategory.computeIfAbsent(candidate.category(), k -> new ArrayList<>()).add(candidate);
        }
        Map<String, CategoryStat> stats = new LinkedHashMap<>();
        for (Map.Entry<String, List<ProductCandidate>> entry : byCategory.entrySet()) {
            stats.put(entry.getKey(), new CategoryStat(entry.getValue()));
        }

        Set<String> categoriesWithoutWarranty = new LinkedHashSet<>();
        for (Map.Entry<String, List<ProductCandidate>> entry : byCategory.entrySet()) {
            boolean anyWarranty = entry.getValue().stream()
                .anyMatch(c -> c.metrics().keySet().stream().anyMatch(k -> k.startsWith("Warranty")));
            if (!anyWarranty) {
                categoriesWithoutWarranty.add(entry.getKey());
            }
        }

        List<ProductScore> scores = new ArrayList<>();
        for (Map.Entry<String, List<ProductCandidate>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            CategoryStat stat = stats.get(category);
            Map<String, BudgetRequest.CategoryRequirement> requirements = request.byCategory();
            BudgetRequest.CategoryRequirement requirement = requirements.get(category);
            String preferredBrand = requirement == null ? null : requirement.preferredBrand();
            BigDecimal target = requirement == null ? null : requirement.targetUnitPrice();
            boolean warrantyAvailable = !categoriesWithoutWarranty.contains(category);

            for (ProductCandidate candidate : entry.getValue()) {
                scores.add(score(candidate, stat, request.weights(), preferredBrand, target, warrantyAvailable));
            }
            if (!warrantyAvailable) {
                limitations.add("Warranty: no retailer key in category '" + category
                    + "' records a warranty, so warranty suitability was left out of the score"
                    + " for that category instead of being guessed.");
            }
            long withoutSpecs = entry.getValue().stream().filter(c -> c.metrics().isEmpty()).count();
            if (withoutSpecs > 0) {
                limitations.add(withoutSpecs + " candidate(s) in '" + category
                    + "' publish no comparable specification; their specification and value components"
                    + " are the neutral 0.5, not a full score.");
            }
        }
        scores.sort(Comparator.comparing((ProductScore s) -> s.candidate().category())
            .thenComparing(s -> s.candidate().productId())
            .thenComparing(s -> s.candidate().store()));
        return new ScoringOutcome(scores, limitations);
    }

    private ProductScore score(ProductCandidate candidate, CategoryStat stat, PreferenceWeights weights,
                               String preferredBrand, BigDecimal targetPrice, boolean warrantyAvailable) {
        Map<ScoringCriterion, Double> components = new EnumMap<>(ScoringCriterion.class);
        Map<ScoringCriterion, Integer> usedWeights = new EnumMap<>(ScoringCriterion.class);
        List<String> notes = new ArrayList<>();

        // price
        double priceComponent;
        if (targetPrice != null && targetPrice.signum() > 0) {
            double ratio = targetPrice.doubleValue() / candidate.unitPrice().doubleValue();
            priceComponent = BudgetMoney.clamp(ratio, 0.0, 1.0);
            notes.add("price component measured against your target of "
                + BudgetMoney.format(targetPrice, candidate.currency()));
        } else {
            priceComponent = stat.priceScore(candidate.unitPrice());
        }
        components.put(ScoringCriterion.PRICE, priceComponent);

        // specifications
        Double specRaw = stat.specScore(candidate);
        boolean specAvailable = specRaw != null;
        double specComponent = specAvailable ? specRaw : NEUTRAL;
        components.put(ScoringCriterion.SPECIFICATIONS, specComponent);
        if (!specAvailable) {
            notes.add("no comparable specification recorded for this product; neutral 0.5 used");
        } else if (candidate.attributeCoverage() < 1.0) {
            notes.add("publishes " + candidate.attributesPresent() + " of " + candidate.attributesTotal()
                + " comparable specifications");
        }

        // value for money
        Double valueRaw = specAvailable ? stat.valueScore(candidate) : null;
        components.put(ScoringCriterion.VALUE, valueRaw == null ? NEUTRAL : valueRaw);
        if (valueRaw == null) {
            notes.add("value for money cannot be measured without specification data; neutral 0.5 used");
        }

        // brand
        boolean brandKnown = preferredBrand != null && !preferredBrand.isBlank();
        if (brandKnown) {
            double brand = preferredBrand.equalsIgnoreCase(candidate.brand()) ? 1.0 : 0.0;
            components.put(ScoringCriterion.BRAND, brand);
            if (brand < 1.0) {
                notes.add("brand '" + candidate.brand() + "' is not your preferred '" + preferredBrand + "'");
            }
        }

        // warranty
        if (warrantyAvailable) {
            Double warrantyRaw = stat.metricScore(candidate, "Warranty");
            components.put(ScoringCriterion.WARRANTY, warrantyRaw == null ? NEUTRAL : warrantyRaw);
            if (warrantyRaw == null) {
                notes.add("no warranty recorded for this product; neutral 0.5 used");
            }
        }

        double weightSum = 0.0;
        double weighted = 0.0;
        for (Map.Entry<ScoringCriterion, Double> entry : components.entrySet()) {
            int weight = weights.of(entry.getKey());
            if (weight == 0) {
                continue;
            }
            usedWeights.put(entry.getKey(), weight);
            weightSum += weight;
            weighted += weight * entry.getValue();
        }
        if (weightSum == 0.0) {
            // every weighted criterion was dropped; fall back to an unweighted mean of what exists
            double mean = components.values().stream().mapToDouble(Double::doubleValue).average().orElse(NEUTRAL);
            usedWeights.put(ScoringCriterion.PRICE, 100);
            return new ProductScore(candidate, components, usedWeights, mean, specAvailable, brandKnown, notes);
        }
        double total = weighted / weightSum;
        return new ProductScore(candidate, components, usedWeights, total, specAvailable, brandKnown, notes);
    }

    /** Per-category normalisation reference points. */
    private static final class CategoryStat {
        private final double minPrice;
        private final double maxPrice;
        private final Map<String, double[]> metricRanges = new LinkedHashMap<>();
        private final double minValueRatio;
        private final double maxValueRatio;

        CategoryStat(List<ProductCandidate> candidates) {
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (ProductCandidate candidate : candidates) {
                double price = candidate.unitPrice().doubleValue();
                low = Math.min(low, price);
                high = Math.max(high, price);
                candidate.metrics().forEach((label, value) -> {
                    double[] range = metricRanges.computeIfAbsent(label,
                        k -> new double[]{Double.MAX_VALUE, -Double.MAX_VALUE});
                    range[0] = Math.min(range[0], value);
                    range[1] = Math.max(range[1], value);
                });
            }
            minPrice = low == Double.MAX_VALUE ? 0 : low;
            maxPrice = high == -Double.MAX_VALUE ? 0 : high;

            double lowRatio = Double.MAX_VALUE;
            double highRatio = -Double.MAX_VALUE;
            for (ProductCandidate candidate : candidates) {
                Double ratio = rawValueRatio(candidate);
                if (ratio != null) {
                    lowRatio = Math.min(lowRatio, ratio);
                    highRatio = Math.max(highRatio, ratio);
                }
            }
            minValueRatio = lowRatio == Double.MAX_VALUE ? 0 : lowRatio;
            maxValueRatio = highRatio == -Double.MAX_VALUE ? 0 : highRatio;
        }

        double priceScore(BigDecimal price) {
            double value = price.doubleValue();
            if (maxPrice - minPrice <= 1e-9) {
                return 1.0; // only one price point: every candidate is equally (un)distinct
            }
            return BudgetMoney.clamp((maxPrice - value) / (maxPrice - minPrice), 0.0, 1.0);
        }

        Double specScore(ProductCandidate candidate) {
            if (candidate.metrics().isEmpty()) {
                return null;
            }
            double sum = 0.0;
            int count = 0;
            for (Map.Entry<String, Double> metric : candidate.metrics().entrySet()) {
                double[] range = metricRanges.get(metric.getKey());
                if (range == null) {
                    continue;
                }
                double normalised = range[1] - range[0] <= 1e-9
                    ? 1.0
                    : (metric.getValue() - range[0]) / (range[1] - range[0]);
                sum += BudgetMoney.clamp(normalised, 0.0, 1.0);
                count++;
            }
            if (count == 0) {
                return null;
            }
            double mean = sum / count;
            double coverage = BudgetMoney.clamp(candidate.attributeCoverage(), 0.0, 1.0);
            return BudgetMoney.clamp(0.7 * mean + 0.3 * coverage, 0.0, 1.0);
        }

        Double metricScore(ProductCandidate candidate, String metricPrefix) {
            for (Map.Entry<String, Double> metric : candidate.metrics().entrySet()) {
                if (!metric.getKey().startsWith(metricPrefix)) {
                    continue;
                }
                double[] range = metricRanges.get(metric.getKey());
                if (range == null) {
                    return null;
                }
                if (range[1] - range[0] <= 1e-9) {
                    return 1.0;
                }
                return BudgetMoney.clamp((metric.getValue() - range[0]) / (range[1] - range[0]), 0.0, 1.0);
            }
            return null;
        }

        Double valueScore(ProductCandidate candidate) {
            Double ratio = rawValueRatio(candidate);
            if (ratio == null) {
                return null;
            }
            if (maxValueRatio - minValueRatio <= 1e-9) {
                return NEUTRAL; // a single ratio point cannot differentiate candidates
            }
            return BudgetMoney.clamp((ratio - minValueRatio) / (maxValueRatio - minValueRatio), 0.0, 1.0);
        }

        private Double rawValueRatio(ProductCandidate candidate) {
            Double spec = specScore(candidate);
            if (spec == null) {
                return null;
            }
            double price = Math.max(candidate.unitPrice().doubleValue(), 0.01);
            return BigDecimal.valueOf(spec / price).round(MathContext.DECIMAL64).doubleValue();
        }
    }
}
