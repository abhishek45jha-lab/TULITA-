package tulita.budget.model;

import tulita.budget.service.BudgetMoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User weights for the recommendation score.
 *
 * <p>Weights are integer percentages (0-100). They are not required to sum to 100: {@link
 * #normalized()} divides by the actual sum, so the interface can show "raw" weights while the
 * engine always works with a normalised vector. A vector whose sum is zero is rejected, because a
 * score with no criteria would silently rank products at random.</p>
 */
public record PreferenceWeights(int price, int specifications, int value, int brand, int warranty) {

    public static final PreferenceWeights DEFAULT = new PreferenceWeights(30, 30, 20, 10, 10);

    public PreferenceWeights {
        for (int w : new int[]{price, specifications, value, brand, warranty}) {
            if (w < 0 || w > 100) {
                throw new IllegalArgumentException("Each preference weight must be between 0 and 100.");
            }
        }
        if (price + specifications + value + brand + warranty == 0) {
            throw new IllegalArgumentException("At least one preference weight must be greater than zero.");
        }
    }

    public static PreferenceWeights balanced() {
        return new PreferenceWeights(25, 30, 25, 10, 10);
    }

    public int total() {
        return price + specifications + value + brand + warranty;
    }

    /** Weights divided by their own sum, so they always add up to 1.0. */
    public Map<ScoringCriterion, Double> normalized() {
        Map<ScoringCriterion, Double> out = new EnumMap<>(ScoringCriterion.class);
        double sum = total();
        out.put(ScoringCriterion.PRICE, price / sum);
        out.put(ScoringCriterion.SPECIFICATIONS, specifications / sum);
        out.put(ScoringCriterion.VALUE, value / sum);
        out.put(ScoringCriterion.BRAND, brand / sum);
        out.put(ScoringCriterion.WARRANTY, warranty / sum);
        return out;
    }

    public int of(ScoringCriterion criterion) {
        return switch (criterion) {
            case PRICE -> price;
            case SPECIFICATIONS -> specifications;
            case VALUE -> value;
            case BRAND -> brand;
            case WARRANTY -> warranty;
        };
    }

    public PreferenceWeights with(ScoringCriterion criterion, int weight) {
        return switch (criterion) {
            case PRICE -> new PreferenceWeights(weight, specifications, value, brand, warranty);
            case SPECIFICATIONS -> new PreferenceWeights(price, weight, value, brand, warranty);
            case VALUE -> new PreferenceWeights(price, specifications, weight, brand, warranty);
            case BRAND -> new PreferenceWeights(price, specifications, value, weight, warranty);
            case WARRANTY -> new PreferenceWeights(price, specifications, value, brand, weight);
        };
    }

    /** Human readable form used in the saved report and in the interface. */
    public String describe() {
        return "price " + price + " %, specifications " + specifications + " %, value " + value
            + " %, brand " + brand + " %, warranty " + warranty + " %";
    }

    public Map<String, String> asText() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("price", Integer.toString(price));
        out.put("specifications", Integer.toString(specifications));
        out.put("value", Integer.toString(value));
        out.put("brand", Integer.toString(brand));
        out.put("warranty", Integer.toString(warranty));
        return out;
    }

    /** Convenience for tests and callers that carry weights as decimals. */
    public static PreferenceWeights ofDecimals(BigDecimal price, BigDecimal specifications, BigDecimal value,
                                               BigDecimal brand, BigDecimal warranty) {
        return new PreferenceWeights(toInt(price), toInt(specifications), toInt(value), toInt(brand),
            toInt(warranty));
    }

    private static int toInt(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** Rounded percentage of a normalised weight, used by the explanation text. */
    public String describePercent(ScoringCriterion criterion) {
        return BudgetMoney.percent(of(criterion) * 100.0 / total());
    }
}
