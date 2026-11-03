package tulita.budget.service;

import tulita.budget.engine.BudgetOptimizationEngine;
import tulita.budget.engine.OptimizationConstraintValidator;
import tulita.budget.model.BudgetRecommendation;
import tulita.budget.model.BudgetRequest;
import tulita.budget.model.OptimizationResult;
import tulita.budget.model.ProductCandidate;
import tulita.budget.model.ProductScore;
import tulita.budget.model.LineCharges;
import tulita.budget.model.RecommendationAlternative;
import tulita.budget.model.RecommendationDelta;
import tulita.budget.model.WhatIfOption;
import tulita.budget.model.RecommendationMode;
import tulita.budget.model.SelectedProduct;
import tulita.photocatalog.model.PhotoProduct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Entry point of the budget planner.
 *
 * <p>Reads the same validated catalogue the comparison screens use, scores its candidates, runs the
 * search once and turns the Pareto front into one recommendation plus the alternatives that a different
 * objective would choose. It contains no user-interface code and needs no database, so it is fully
 * testable on its own.</p>
 */
public final class BudgetOptimizerService {

    private final ProductScoringService scoringService = new ProductScoringService();
    private final BudgetCalculationService calculationService = new BudgetCalculationService();
    private final BudgetOptimizationEngine engine = new BudgetOptimizationEngine(calculationService);
    private final OptimizationConstraintValidator validator = new OptimizationConstraintValidator();
    private final RecommendationExplanationService explanations = new RecommendationExplanationService();

    /** Categories that really exist in the supplied catalogue, with product counts. */
    public Map<String, Integer> categories(List<PhotoProduct> products) {
        Map<String, Integer> counts = new TreeMap<>();
        for (PhotoProduct product : products) {
            counts.merge(product.category(), 1, Integer::sum);
        }
        return counts;
    }

    /** Brands per category, for the preferred-brand pickers. */
    public Map<String, List<String>> brandsByCategory(List<PhotoProduct> products) {
        Map<String, Set<String>> sets = new TreeMap<>();
        for (PhotoProduct product : products) {
            sets.computeIfAbsent(product.category(), k -> new LinkedHashSet<>()).add(product.brand());
        }
        Map<String, List<String>> out = new TreeMap<>();
        sets.forEach((category, brands) -> {
            List<String> sorted = new ArrayList<>(brands);
            sorted.sort(String::compareToIgnoreCase);
            out.put(category, sorted);
        });
        return out;
    }

    /** Spec metrics available for a category (used by the minimum-specification pickers). */
    public List<String> specMetrics(String category) {
        return CategorySpecProfiles.forCategory(category).labels();
    }

    /** The catalogue's currency; the planner never mixes or relabels currencies. */
    public String currency(List<PhotoProduct> products) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PhotoProduct product : products) {
            for (PhotoProduct.Offer offer : product.offers()) {
                counts.merge(offer.currency(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse("USD");
    }

    /**
     * Scores and optimises one request.
     *
     * @throws OptimizationConstraintValidator.InvalidRequestException when the request itself is invalid
     *         (negative budget, impossible quantities, no required category, unknown specification)
     */
    public OptimizationResult optimize(List<PhotoProduct> products, BudgetRequest request) {
        validator.validateRequest(request);
        if (products == null || products.isEmpty()) {
            return OptimizationResult.infeasible(List.of("The catalogue is empty, so nothing can be suggested."),
                List.of("Load the catalogue first, then optimise."), List.of(), engineNote(0, true));
        }
        String currency = currency(products);
        if (!currency.equalsIgnoreCase(request.currency())) {
            throw new IllegalArgumentException("The catalogue is priced in " + currency
                + ", so a " + request.currency() + " budget cannot be used without a conversion this project"
                + " does not perform.");
        }

        Map<String, Integer> catalogue = categories(products);
        List<String> missing = request.categories().stream().map(BudgetRequest.CategoryRequirement::category)
            .filter(category -> !catalogue.containsKey(category)).toList();
        if (!missing.isEmpty()) {
            return OptimizationResult.infeasible(
                List.of("These categories are not in the catalogue: " + String.join(", ", missing) + "."),
                List.of("Pick categories from the list the catalogue actually offers: "
                    + String.join(", ", catalogue.keySet()) + "."),
                explanations.limitations(currency), engineNote(0, true));
        }

        Set<String> requestedCategories = request.byCategory().keySet();
        List<ProductCandidate> candidates = scoringService.candidates(products, requestedCategories);
        ProductScoringService.ScoringOutcome scored = scoringService.scoreAll(candidates, request);
        BudgetOptimizationEngine.Front front = engine.search(request, scored.scores());

        List<String> limitations = new ArrayList<>(explanations.limitations(currency));
        limitations.addAll(scored.limitations());
        limitations.addAll(front.notes());

        if (front.combinations().isEmpty()) {
            List<String> reasons = new ArrayList<>(front.notes());
            List<String> emptyCategories = request.categories().stream()
                .filter(requirement -> scored.forCategory(requirement.category()).isEmpty())
                .map(BudgetRequest.CategoryRequirement::category).toList();
            BigDecimal cheapest = cheapestRequiredEstimate(request, scored.scores());
            List<String> suggestions = explanations.suggestions(request, cheapest, emptyCategories);
            return OptimizationResult.infeasible(reasons, suggestions, limitations, front.algorithmNote());
        }

        Map<String, List<ProductScore>> poolByCategory = new LinkedHashMap<>();
        for (ProductScore score : scored.scores()) {
            poolByCategory.computeIfAbsent(score.candidate().category(), k -> new ArrayList<>()).add(score);
        }

        BudgetOptimizationEngine.Combination chosen = engine.select(front.combinations(), request.mode(), request);
        if (chosen == null) {
            return OptimizationResult.infeasible(
                List.of("No combination satisfied every mandatory requirement inside the budget."),
                explanations.suggestions(request, cheapestRequiredEstimate(request, scored.scores()), List.of()),
                limitations, front.algorithmNote());
        }
        BudgetRecommendation primary = build(request, chosen, poolByCategory, limitations);
        List<String> violations = validator.violations(request, primary.selected(), primary.breakdown().total(),
            catalogue.keySet());
        if (!violations.isEmpty()) {
            throw new IllegalStateException("The optimiser produced an invalid basket: "
                + String.join(" ", violations));
        }

        List<RecommendationAlternative> alternatives = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();
        signatures.add(primary.signature());
        for (RecommendationMode mode : RecommendationMode.values()) {
            if (mode == request.mode()) {
                continue;
            }
            BudgetOptimizationEngine.Combination combination = engine.select(front.combinations(), mode, request);
            if (combination == null) {
                continue;
            }
            BudgetRecommendation candidate = build(request.withMode(mode), combination, poolByCategory, limitations);
            if (!signatures.add(candidate.signature())) {
                continue;
            }
            alternatives.add(new RecommendationAlternative(mode, mode.label() + " basket", candidate,
                candidate.signature(), explanations.differences(primary, candidate),
                explanations.advantages(primary, candidate), explanations.disadvantages(primary, candidate)));
        }

        return new OptimizationResult(true, primary, alternatives, List.of(), List.of(), limitations,
            front.exhaustive(), front.combinations().size(), front.algorithmNote()
                + " Objective for " + request.mode().label() + ": " + BudgetOptimizationEngine
                .objectiveStatement(request.mode()) + ".");
    }

    private BudgetRecommendation build(BudgetRequest request, BudgetOptimizationEngine.Combination combination,
                                       Map<String, List<ProductScore>> poolByCategory, List<String> limitations) {
        List<SelectedProduct> lines = new ArrayList<>();
        for (BudgetOptimizationEngine.Draft draft : combination.drafts()) {
            ProductScore score = draft.option().score();
            String category = draft.category();
            BudgetRequest.CategoryRequirement requirement = request.byCategory().get(category);
            double priority = requirement == null || requirement.priority() == null
                ? 1.0 : requirement.priority().factor();
            List<ProductScore> pool = poolByCategory.getOrDefault(category, List.of());
            String explanation = explanations.explain(score, pool, requirement, draft.option().quantity());
            String tradeOff = explanations.tradeOff(score, pool, requirement);
            lines.add(SelectedProduct.of(score.candidate(), draft.option().quantity(), score, priority,
                explanation, tradeOff));
        }
        lines.sort(Comparator.comparing(line -> line.candidate().category()));
        BudgetCalculationService.Costs costs = calculationService.costs(request, lines);
        var breakdown = calculationService.breakdown(request, costs);
        List<String> satisfied = new ArrayList<>();
        for (SelectedProduct line : lines) {
            if (!satisfied.contains(line.candidate().category())) {
                satisfied.add(line.candidate().category());
            }
        }
        String headline = request.mode().label() + ": " + combination.itemCount() + " item(s), "
            + BudgetMoney.format(breakdown.total(), request.currency()) + " of your "
            + BudgetMoney.format(request.budget(), request.currency()) + ", TULITA Recommendation Score "
            + BudgetMoney.score(combination.averageScore()) + " average.";
        return new BudgetRecommendation(request.mode(), lines, breakdown, combination.scoreSum(),
            combination.averageScore(), satisfied, limitations, headline);
    }

    /**
     * A deliberately crude reference figure for the "raise the budget" advice: the cheapest eligible
     * product per required category at its minimum quantity, plus the delivery fees of those shops.
     */
    private BigDecimal cheapestRequiredEstimate(BudgetRequest request, List<ProductScore> scores) {
        BigDecimal total = BigDecimal.ZERO;
        Set<String> stores = new LinkedHashSet<>();
        boolean any = false;
        for (BudgetRequest.CategoryRequirement requirement : request.categories()) {
            if (!requirement.required()) {
                continue;
            }
            List<ProductScore> pool = scores.stream()
                .filter(s -> s.candidate().category().equals(requirement.category()))
                .filter(s -> requirement.minimumSpec() == null
                    || (s.candidate().metrics().containsKey(requirement.minimumSpec().metric())
                    && s.candidate().metrics().get(requirement.minimumSpec().metric()) + 1e-9
                    >= requirement.minimumSpec().minimum()))
                .toList();
            if (pool.isEmpty()) {
                return null;
            }
            ProductScore cheapest = pool.stream()
                .min(Comparator.comparing(s -> s.candidate().unitPrice())).orElseThrow();
            total = total.add(BudgetMoney.multiply(cheapest.candidate().unitPrice(), requirement.effectiveMin()));
            stores.add(cheapest.candidate().store());
            any = true;
        }
        if (!any) {
            return null;
        }
        for (String store : stores) {
            total = total.add(request.feeFor(store));
        }
        return BudgetMoney.scale(total);
    }

    private String engineNote(int states, boolean exhaustive) {
        return "Pareto dynamic programming; " + states + " states, "
            + (exhaustive ? "exhaustive" : "bounded heuristic") + ".";
    }

    /** Per-line share of the delivery fee and discount, so each card can show its own final cost. */
    public Map<String, LineCharges> lineCharges(BudgetRequest request, BudgetRecommendation recommendation) {
        return calculationService.allocate(request, recommendation.selected());
    }

    /**
     * The what-if variants offered for a finished recommendation.
     *
     * <p>Every option is a complete alternative request with the same mandatory rules, so none of them can
     * quietly relax a constraint. Options that make no sense for the current request (for example lowering a
     * budget that is already below the cheapest basket) are offered anyway, because the honest answer to
     * "what if I had less money" is "no valid combination" and the shopper should be able to see that.</p>
     */
    public List<WhatIfOption> whatIfOptions(BudgetRequest request) {
        List<WhatIfOption> options = new ArrayList<>();
        BigDecimal lower = BudgetMoney.scale(request.budget().multiply(new BigDecimal("0.9")));
        BigDecimal higher = BudgetMoney.scale(request.budget().multiply(new BigDecimal("1.1")));
        if (lower.signum() > 0 && lower.compareTo(request.budget()) != 0) {
            options.add(new WhatIfOption("Lower budget by 10 %",
                "same requirements with " + BudgetMoney.format(lower, request.currency()) + " instead of "
                    + BudgetMoney.format(request.budget(), request.currency()), request.withBudget(lower)));
        }
        options.add(new WhatIfOption("Raise budget by 10 %",
            "same requirements with " + BudgetMoney.format(higher, request.currency()) + " instead of "
                + BudgetMoney.format(request.budget(), request.currency()), request.withBudget(higher)));

        for (RecommendationMode mode : RecommendationMode.values()) {
            if (mode == request.mode()) {
                continue;
            }
            options.add(new WhatIfOption("Switch to " + mode.label(), mode.description(),
                request.withMode(mode)));
        }

        for (BudgetRequest.CategoryRequirement requirement : request.categories()) {
            if (!requirement.required()) {
                continue;
            }
            if (request.requiredCategories().size() == 1) {
                continue; // the last mandatory category cannot be dropped: nothing would be left to optimise
            }
            if (requirement.minQuantity() > 1) {
                int reduced = requirement.minQuantity() - 1;
                List<BudgetRequest.CategoryRequirement> updated = replace(request, requirement,
                    new BudgetRequest.CategoryRequirement(requirement.category(), true, reduced,
                        Math.max(reduced, requirement.maxQuantity()), requirement.preferredBrand(),
                        requirement.priority(), requirement.targetUnitPrice(), requirement.minimumSpec()));
                options.add(new WhatIfOption("One less " + requirement.category(),
                    "reduce the minimum quantity from " + requirement.minQuantity() + " to " + reduced,
                    request.withCategories(updated)));
            } else {
                List<BudgetRequest.CategoryRequirement> updated = replace(request, requirement,
                    new BudgetRequest.CategoryRequirement(requirement.category(), false, 0,
                        Math.max(1, requirement.maxQuantity()), requirement.preferredBrand(),
                        requirement.priority(), requirement.targetUnitPrice(), requirement.minimumSpec()));
                options.add(new WhatIfOption("Make " + requirement.category() + " optional",
                    "keep it only if it still fits the budget", request.withCategories(updated)));
            }
            if (requirement.preferredBrand() != null) {
                List<BudgetRequest.CategoryRequirement> updated = replace(request, requirement,
                    new BudgetRequest.CategoryRequirement(requirement.category(), requirement.required(),
                        requirement.minQuantity(), requirement.maxQuantity(), null, requirement.priority(),
                        requirement.targetUnitPrice(), requirement.minimumSpec()));
                options.add(new WhatIfOption("Any brand for " + requirement.category(),
                    "drop the preference for " + requirement.preferredBrand(), request.withCategories(updated)));
            }
            if (requirement.minimumSpec() != null) {
                List<BudgetRequest.CategoryRequirement> updated = replace(request, requirement,
                    new BudgetRequest.CategoryRequirement(requirement.category(), requirement.required(),
                        requirement.minQuantity(), requirement.maxQuantity(), requirement.preferredBrand(),
                        requirement.priority(), requirement.targetUnitPrice(), null));
                options.add(new WhatIfOption("Relax the minimum for " + requirement.category(),
                    "remove the mandatory " + requirement.minimumSpec().describe(),
                    request.withCategories(updated)));
            }
        }

        if (request.hasCharges()) {
            options.add(new WhatIfOption("Remove my charges",
                "compare item prices with no delivery fee and no discount",
                request.withCharges(Map.of(), BudgetMoney.ZERO, BudgetMoney.ZERO)));
        }
        return options;
    }

    private List<BudgetRequest.CategoryRequirement> replace(BudgetRequest request,
                                                            BudgetRequest.CategoryRequirement target,
                                                            BudgetRequest.CategoryRequirement replacement) {
        List<BudgetRequest.CategoryRequirement> out = new ArrayList<>();
        for (BudgetRequest.CategoryRequirement requirement : request.categories()) {
            out.add(requirement.category().equals(target.category()) ? replacement : requirement);
        }
        return out;
    }

    /** Change between the recommendation that was on screen and the one just produced. */
    public RecommendationDelta delta(BudgetRecommendation previous, BudgetRecommendation current) {
        if (previous == null || current == null) {
            return null;
        }
        boolean same = previous.signature().equals(current.signature());
        return new RecommendationDelta(previous.breakdown().total(), current.breakdown().total(),
            previous.breakdown().remaining(), current.breakdown().remaining(),
            previous.averageScore(), current.averageScore(), previous.itemCount(), current.itemCount(),
            explanations.differences(previous, current), same, current.breakdown().currency());
    }

    /** Exposed for the interface and for tests that want to check one candidate in isolation. */
    public ProductScoringService scoring() {
        return scoringService;
    }

    public BudgetCalculationService calculation() {
        return calculationService;
    }

    public BudgetOptimizationEngine engine() {
        return engine;
    }

    public OptimizationConstraintValidator validator() {
        return validator;
    }

    public RecommendationExplanationService explanations() {
        return explanations;
    }
}
