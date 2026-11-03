package tulita.budget.service;

import tulita.budget.model.BudgetRequest;
import tulita.budget.model.BudgetRecommendation;
import tulita.budget.model.ProductCandidate;
import tulita.budget.model.ProductScore;
import tulita.budget.model.ScoringCriterion;
import tulita.budget.model.SelectedProduct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds every sentence the planner shows about a choice, from the numbers that produced it.
 *
 * <p>Nothing here invents a reason: each sentence is derived from the scoring components, the
 * constraint evaluation or the price comparison for that category. If a claim cannot be supported (for
 * example "best battery life") it is not made, because the catalogue does not record it.</p>
 */
public final class RecommendationExplanationService {

    /** Why this exact candidate was chosen for this category. */
    public String explain(ProductScore score, List<ProductScore> pool, BudgetRequest.CategoryRequirement requirement,
                          int quantity) {
        ProductCandidate candidate = score.candidate();
        List<String> reasons = new ArrayList<>();

        Optional<ProductScore> cheapest = pool.stream()
            .filter(s -> meetsSpec(s, requirement))
            .min(Comparator.comparing(s -> s.candidate().unitPrice()));
        Optional<ProductScore> strongest = pool.stream()
            .filter(s -> meetsSpec(s, requirement))
            .max(Comparator.comparingDouble(ProductScore::total));

        if (cheapest.isPresent() && cheapest.get().candidate().signature().equals(candidate.signature())) {
            reasons.add("lowest recorded price among the eligible options in this category");
        }
        if (strongest.isPresent() && strongest.get().candidate().signature().equals(candidate.signature())
            && pool.size() > 1) {
            reasons.add("highest TULITA Recommendation Score in this category");
        }
        if (requirement != null && requirement.minimumSpec() != null) {
            Double value = candidate.metrics().get(requirement.minimumSpec().metric());
            if (value != null) {
                reasons.add("meets your mandatory minimum " + requirement.minimumSpec().describe()
                    + " (records " + trim(value) + ")");
            }
        }
        if (requirement != null && requirement.preferredBrand() != null
            && requirement.preferredBrand().equalsIgnoreCase(candidate.brand())) {
            reasons.add("matches your preferred brand " + candidate.brand());
        }
        if (requirement != null && requirement.targetUnitPrice() != null
            && candidate.unitPrice().compareTo(requirement.targetUnitPrice()) <= 0) {
            reasons.add("at or below your target price of "
                + BudgetMoney.format(requirement.targetUnitPrice(), candidate.currency()));
        }
        if (!score.specAvailable()) {
            reasons.add("the catalogue records no comparable specification for it, so it was scored neutrally"
                + " on specifications instead of being rewarded");
        } else if (score.component(ScoringCriterion.SPECIFICATIONS) >= 0.66) {
            reasons.add("strong recorded specifications for the price");
        }
        if (quantity > 1) {
            reasons.add("counted " + quantity + " times as you asked");
        }
        if (reasons.isEmpty()) {
            reasons.add("best weighted balance of " + describeWeighting(requirement) + " among the eligible options");
        }
        String text = "Selected because it has the " + String.join(", ", reasons) + ".";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    /** What the shopper gives up by taking this product instead of the cheaper one in the same category. */
    public String tradeOff(ProductScore chosen, List<ProductScore> pool, BudgetRequest.CategoryRequirement requirement) {
        List<ProductScore> cheaper = pool.stream()
            .filter(s -> meetsSpec(s, requirement))
            .filter(s -> s.candidate().unitPrice().compareTo(chosen.candidate().unitPrice()) < 0)
            .sorted(Comparator.comparing(s -> s.candidate().unitPrice()))
            .toList();
        if (cheaper.isEmpty()) {
            return "No eligible option in this category is cheaper, so nothing was given up on price.";
        }
        ProductScore alternative = cheaper.get(0);
        BigDecimal difference = chosen.candidate().unitPrice().subtract(alternative.candidate().unitPrice());
        double scoreGap = (chosen.total() - alternative.total()) * 100.0;
        StringBuilder out = new StringBuilder();
        out.append("Cheaper alternative: ").append(alternative.candidate().name())
            .append(" at ").append(BudgetMoney.format(alternative.candidate().unitPrice(),
                alternative.candidate().currency()))
            .append(" (").append(alternative.candidate().store()).append(") saves ")
            .append(BudgetMoney.format(difference, chosen.candidate().currency()))
            .append(" but scores ").append(trim(scoreGap)).append(" points lower");
        if (!alternative.specAvailable() && chosen.specAvailable()) {
            out.append(" and publishes no comparable specification");
        } else if (alternative.specAvailable() && chosen.specAvailable()) {
            out.append(" and records ").append(alternative.candidate().describeSpecs());
        }
        out.append('.');
        return out.toString();
    }

    /** Differences between the primary basket and an alternative, category by category. */
    public List<String> differences(BudgetRecommendation primary, BudgetRecommendation alternative) {
        Map<String, SelectedProduct> left = index(primary);
        Map<String, SelectedProduct> right = index(alternative);
        List<String> out = new ArrayList<>();
        for (String category : union(left, right)) {
            SelectedProduct a = left.get(category);
            SelectedProduct b = right.get(category);
            if (a == null) {
                out.add(category + ": added — " + label(b) + " (" + money(b)
                    + ") instead of leaving this category empty");
            } else if (b == null) {
                out.add(category + ": removed — " + label(a) + " (" + money(a) + ") is not bought");
            } else if (!a.candidate().signature().equals(b.candidate().signature())
                || a.quantity() != b.quantity()) {
                out.add(category + ": " + label(a) + " (" + money(a) + ") becomes " + label(b) + " ("
                    + money(b) + ")");
            }
        }
        if (out.isEmpty()) {
            out.add("Identical product lines.");
        }
        return out;
    }

    public List<String> advantages(BudgetRecommendation primary, BudgetRecommendation alternative) {
        List<String> out = new ArrayList<>();
        BigDecimal delta = primary.breakdown().total().subtract(alternative.breakdown().total());
        if (delta.signum() > 0) {
            out.add("Costs " + BudgetMoney.format(delta, alternative.breakdown().currency())
                + " less than the primary recommendation.");
        }
        double scoreDelta = (alternative.scoreSum() - primary.scoreSum()) * 100.0;
        if (scoreDelta > 0.05) {
            out.add("Slightly higher total recommendation score (+" + trim(scoreDelta) + " points).");
        }
        if (alternative.breakdown().headroomPercent() > primary.breakdown().headroomPercent() + 0.05) {
            out.add("Leaves " + BudgetMoney.format(
                alternative.breakdown().remaining().subtract(primary.breakdown().remaining()),
                alternative.breakdown().currency()) + " more of the budget unspent.");
        }
        double specDelta = (alternative.scoreSum() - primary.scoreSum());
        if (out.isEmpty() || Math.abs(specDelta) < 1e-9) {
            out.add("Keeps every mandatory requirement satisfied.");
        }
        return out;
    }

    public List<String> disadvantages(BudgetRecommendation primary, BudgetRecommendation alternative) {
        List<String> out = new ArrayList<>();
        BigDecimal delta = alternative.breakdown().total().subtract(primary.breakdown().total());
        if (delta.signum() > 0) {
            out.add("Costs " + BudgetMoney.format(delta, alternative.breakdown().currency())
                + " more than the primary recommendation.");
        }
        double scoreDelta = (primary.scoreSum() - alternative.scoreSum()) * 100.0;
        if (scoreDelta > 0.05) {
            out.add("Lower total recommendation score (-" + trim(scoreDelta) + " points) under your weights.");
        }
        if (alternative.itemCount() < primary.itemCount()) {
            out.add("Contains fewer items: " + alternative.itemCount() + " instead of " + primary.itemCount() + ".");
        }
        if (out.isEmpty()) {
            out.add("No disadvantage beyond the different product mix.");
        }
        return out;
    }

    /** Actionable suggestions when no valid combination exists. */
    public List<String> suggestions(BudgetRequest request, BigDecimal cheapestRequiredEstimate,
                                    List<String> emptyCategories) {
        List<String> out = new ArrayList<>();
        if (cheapestRequiredEstimate != null && cheapestRequiredEstimate.signum() > 0) {
            out.add("The cheapest basket that satisfies your mandatory categories is about "
                + BudgetMoney.format(cheapestRequiredEstimate, request.currency())
                + " before any delivery charge; raise the budget at least to that level.");
            BigDecimal raise = cheapestRequiredEstimate.subtract(request.budget());
            if (raise.signum() > 0) {
                out.add("That is " + BudgetMoney.format(raise, request.currency()) + " more than the budget you entered.");
            }
        }
        for (String category : emptyCategories) {
            out.add("No eligible product exists in '" + category
                + "'; remove it, or relax its minimum specification or brand preference.");
        }
        request.categories().stream().filter(c -> !c.required()).forEach(c ->
            out.add("'" + c.category() + "' is optional, so it can be dropped without breaking a mandatory rule."));
        request.categories().stream()
            .filter(c -> c.maxQuantity() > c.effectiveMin())
            .forEach(c -> out.add("Reduce the quantity for '" + c.category() + "' to " + c.effectiveMin() + "."));
        request.categories().stream().filter(c -> c.preferredBrand() != null).forEach(c ->
            out.add("Clear the preferred brand for '" + c.category() + "' to allow cheaper brands."));
        request.categories().stream().filter(c -> c.minimumSpec() != null).forEach(c ->
            out.add("Lower the minimum specification for '" + c.category() + "' (" + c.minimumSpec().describe()
                + ") or remove it."));
        if (request.hasCharges()) {
            out.add("Delivery charges and discounts you entered change the total; try setting them to zero to"
                + " see whether the items alone fit.");
        }
        return out;
    }

    /** Data limitations that apply to every recommendation from this catalogue. */
    public List<String> limitations(String currency) {
        List<String> out = new ArrayList<>();
        out.add("Prices are the curated historical observations of the catalogue, not live retailer prices,"
            + " and no capture date is recorded in the source.");
        out.add("Delivery charges and discounts are your own entries; the catalogue records none.");
        out.add("Ratings, review counts and stock availability are not in the source, so they influence nothing"
            + " and are never shown as if they did.");
        out.add("Amounts are labelled " + currency + " because that is the source currency; no currency"
            + " conversion is applied.");
        out.add("A basket is a plan against recorded data, not a checkout: availability and final payable"
            + " totals are unknown.");
        return out;
    }

    private boolean meetsSpec(ProductScore score, BudgetRequest.CategoryRequirement requirement) {
        if (requirement == null || requirement.minimumSpec() == null) {
            return true;
        }
        Double value = score.candidate().metrics().get(requirement.minimumSpec().metric());
        return value != null && value + 1e-9 >= requirement.minimumSpec().minimum();
    }

    private String describeWeighting(BudgetRequest.CategoryRequirement requirement) {
        return "price, specifications, value for money and brand fits";
    }

    private String label(SelectedProduct line) {
        return line.candidate().name() + " x" + line.quantity() + " @ " + line.candidate().store();
    }

    private String money(SelectedProduct line) {
        return BudgetMoney.format(line.lineTotal(), line.candidate().currency());
    }

    private Map<String, SelectedProduct> index(BudgetRecommendation recommendation) {
        Map<String, SelectedProduct> out = new LinkedHashMap<>();
        for (SelectedProduct line : recommendation.selected()) {
            out.put(line.candidate().category(), line);
        }
        return out;
    }

    private List<String> union(Map<String, SelectedProduct> a, Map<String, SelectedProduct> b) {
        List<String> keys = new ArrayList<>(a.keySet());
        for (String key : b.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        keys.sort(String::compareTo);
        return keys;
    }

    private static String trim(double value) {
        return BigDecimal.valueOf(value).setScale(2, BudgetMoney.ROUNDING).stripTrailingZeros().toPlainString();
    }
}
