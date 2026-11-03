package tulita.budget.engine;

import tulita.budget.model.BudgetRecommendation;
import tulita.budget.model.BudgetRequest;
import tulita.budget.model.ProductScore;
import tulita.budget.model.RecommendationMode;
import tulita.budget.model.ScoringCriterion;
import tulita.budget.model.SelectedProduct;
import tulita.budget.service.BudgetCalculationService;
import tulita.budget.service.BudgetMoney;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exact budget-constrained combination search with dynamic programming over Pareto-optimal states.
 *
 * <h2>Problem shape</h2>
 * <p>One requirement per category, one product per category (repeated by quantity), and a budget. That
 * is a multiple-choice knapsack with a per-retailer fixed delivery fee, so the transport-style cost
 * depends on <em>which</em> retailers the basket touches. The search therefore carries the set of
 * retailers used as part of its state, which keeps the "pay one delivery per shop" rule exact.</p>
 *
 * <h2>Algorithm</h2>
 * <p>Categories are processed one at a time. Each DP state is
 * {@code (item subtotal in cents, retailer mask, score sum, specification sum, choices)}. After each
 * category the state list is pruned to its Pareto front: a state is discarded when another state is
 * no more expensive and at least as good on every objective. Pruning uses an optimistic lower bound on
 * the final cost (delivery of the retailers already used, with the discount assumed to apply), so a
 * state is only dropped when it cannot fit the budget even under the most favourable completion.</p>
 *
 * <p>Because the front keeps every non-dominated trade-off between cost and the two score objectives,
 * all four recommendation modes read the <em>same</em> front and simply apply their own comparator.
 * The result is exact for this model as long as the front was not truncated; if the safety cap
 * ({@value #MAX_STATES} states) is hit, {@link Front#exhaustive()} is false and the result is reported
 * as a heuristic rather than as the global optimum.</p>
 *
 * <h2>Complexity</h2>
 * <p>O(categories x front size x options per category) time, O(front size) memory. In practice the
 * front stays small because most candidates are dominated, and the catalogue is curated (79 products).</p>
 */
public final class BudgetOptimizationEngine {

    /** Safety cap on the number of states kept after each category. */
    public static final int MAX_STATES = 20_000;

    private final BudgetCalculationService calculator;

    public BudgetOptimizationEngine(BudgetCalculationService calculator) {
        this.calculator = calculator;
    }

    /** One concrete thing the search may buy in a category: a candidate repeated {@code quantity} times. */
    public record Option(ProductScore score, int quantity, long lineCents, double scoreContribution,
                         double specContribution) {

        public String signature() {
            return score == null ? "skip" : score.candidate().signature() + "x" + quantity;
        }
    }

    /** A chosen option, with the category it satisfies. */
    public record Draft(Option option, String category) {
    }

    /** A complete basket found by the search. */
    public record Combination(List<Draft> drafts, long totalCents, double scoreSum, double specSum) {

        public Combination {
            drafts = List.copyOf(drafts);
        }

        public int itemCount() {
            return drafts.stream().mapToInt(d -> d.option().quantity()).sum();
        }

        public double averageScore() {
            int units = itemCount();
            return units == 0 ? 0.0 : scoreSum / units;
        }

        public BigDecimal total() {
            return BudgetMoney.fromCents(totalCents);
        }

        /** Deterministic identity, used to drop duplicate alternatives. */
        public String signature() {
            List<String> parts = new ArrayList<>();
            for (Draft draft : drafts) {
                parts.add(draft.option().signature());
            }
            parts.sort(String::compareTo);
            return String.join(";", parts);
        }

        public String describe() {
            StringBuilder out = new StringBuilder();
            for (Draft draft : drafts) {
                if (out.length() > 0) {
                    out.append(" · ");
                }
                out.append(draft.option().score().candidate().name())
                    .append(" x").append(draft.option().quantity())
                    .append(" @ ").append(draft.option().score().candidate().store());
            }
            return out.toString();
        }
    }

    /** Search output: the Pareto front plus what the search had to do to get there. */
    public record Front(List<Combination> combinations, boolean exhaustive, int statesEvaluated,
                        List<String> notes) {

        public Front {
            combinations = List.copyOf(combinations);
            notes = List.copyOf(notes);
        }

        public String algorithmNote() {
            String base = "Pareto dynamic programming over " + statesEvaluated + " surviving states, one"
                + " product per category repeated by quantity, delivery charged once per retailer.";
            return exhaustive ? base + " The front was not truncated, so the chosen combination is optimal"
                + " for the selected objective within this model."
                : base + " The state cap was reached, so this is a bounded heuristic and not a proof of"
                + " global optimality.";
        }
    }

    /**
     * Runs the search.
     *
     * @param scores every scored candidate for the requested categories
     */
    public Front search(BudgetRequest request, List<ProductScore> scores) {
        List<String> notes = new ArrayList<>();
        List<BudgetRequest.CategoryRequirement> requirements = new ArrayList<>(request.categories());
        requirements.sort(Comparator.comparing(BudgetRequest.CategoryRequirement::category));

        Map<String, List<ProductScore>> pool = new LinkedHashMap<>();
        for (ProductScore score : scores) {
            pool.computeIfAbsent(score.candidate().category(), k -> new ArrayList<>()).add(score);
        }

        // deterministic retailer indexing for the delivery mask
        Set<String> stores = new LinkedHashSet<>();
        for (ProductScore score : scores) {
            stores.add(score.candidate().store());
        }
        if (stores.size() > 30) {
            throw new IllegalStateException("Too many distinct retailers for the search state mask.");
        }
        List<String> storeOrder = new ArrayList<>(stores);
        storeOrder.sort(String::compareTo);
        Map<String, Integer> storeIndex = new LinkedHashMap<>();
        Map<String, Long> feeCents = new LinkedHashMap<>();
        for (int i = 0; i < storeOrder.size(); i++) {
            storeIndex.put(storeOrder.get(i), i);
            feeCents.put(storeOrder.get(i), BudgetMoney.cents(request.feeFor(storeOrder.get(i))));
        }

        long budgetCents = BudgetMoney.cents(request.budget());

        // feasibility: a required category with no usable option ends the search immediately
        Map<String, List<Option>> optionsByCategory = new LinkedHashMap<>();
        List<String> emptyCategories = new ArrayList<>();
        List<String> unaffordableCategories = new ArrayList<>();
        for (BudgetRequest.CategoryRequirement requirement : requirements) {
            List<ProductScore> categoryScores = pool.getOrDefault(requirement.category(), List.of());
            boolean eligibleExists = categoryScores.stream().anyMatch(s -> meetsMinimumSpec(s, requirement));
            List<Option> options = options(requirement, categoryScores, request, budgetCents);
            if (!eligibleExists) {
                emptyCategories.add(requirement.category());
            } else if (options.isEmpty()) {
                unaffordableCategories.add(requirement.category());
            }
            optionsByCategory.put(requirement.category(), options);
        }
        List<String> blocking = new ArrayList<>();
        if (!emptyCategories.isEmpty()) {
            blocking.add("No catalogue product in these categories can be used at all: "
                + String.join(", ", emptyCategories) + ".");
        }
        if (!unaffordableCategories.isEmpty()) {
            blocking.add("The budget is smaller than the cheapest eligible product in: "
                + String.join(", ", unaffordableCategories) + ".");
        }
        if (!blocking.isEmpty()) {
            return new Front(List.of(), true, 0, blocking);
        }

        List<State> states = new ArrayList<>();
        states.add(new State(0L, 0, 0.0, 0.0, null));
        boolean truncated = false;

        for (BudgetRequest.CategoryRequirement requirement : requirements) {
            List<Option> options = optionsByCategory.get(requirement.category());
            List<State> next = new ArrayList<>(Math.min(MAX_STATES, states.size() * Math.max(1, options.size())));
            for (State state : states) {
                for (Option option : options) {
                    long items = state.itemsCents + option.lineCents();
                    int mask = option.score() == null
                        ? state.mask                                          // optional category skipped
                        : state.mask | (1 << storeIndex.get(option.score().candidate().store()));
                    long lowerBound = calculator.lowerBoundCents(request, items, storesOf(mask, storeOrder), feeCents);
                    if (lowerBound > budgetCents) {
                        continue;
                    }
                    next.add(new State(items, mask, state.scoreSum + option.scoreContribution(),
                        state.specSum + option.specContribution(),
                        new Choice(option, requirement.category(), state.choices)));
                }
            }
            if (next.isEmpty()) {
                return new Front(List.of(), true, 0, List.of(
                    "Every combination was already over budget after category '" + requirement.category()
                        + "'. The most likely cause is a budget that is too small for the required items."));
            }
            states = prune(next, storeOrder, feeCents, request, budgetCents);
            if (states.size() > MAX_STATES) {
                states.sort(Comparator.comparingDouble(State::scoreSum).reversed());
                states = new ArrayList<>(states.subList(0, MAX_STATES));
                truncated = true;
            }
        }

        List<Combination> combinations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (State state : states) {
            long totalCents = calculator.totalCents(request, state.itemsCents, storesOf(state.mask, storeOrder),
                feeCents);
            if (totalCents > budgetCents) {
                continue;
            }
            List<Draft> drafts = new ArrayList<>();
            for (Choice choice = state.choices; choice != null; choice = choice.prev) {
                if (choice.option.score() == null) {
                    continue; // the optional category was skipped, so it contributes no line
                }
                drafts.add(new Draft(choice.option, choice.category));
            }
            drafts.sort(Comparator.comparing(Draft::category));
            Combination combination = new Combination(drafts, totalCents, state.scoreSum, state.specSum);
            if (seen.add(combination.signature())) {
                combinations.add(combination);
            }
        }
        if (truncated) {
            notes.add("The Pareto front reached the " + MAX_STATES + "-state safety cap, so the search was"
                + " bounded. The recommendation is still valid and within budget, but it is reported as a"
                + " heuristic rather than as a proven global optimum.");
        }
        notes.add("Modelling note: each category buys one chosen product, repeated by the quantity you"
            + " set. Two different products from the same category appear as two categories.");
        return new Front(combinations, !truncated, combinations.size(), notes);
    }

    /** Picks the best combination of the front for one objective. */
    public Combination select(List<Combination> combinations, RecommendationMode mode, BudgetRequest request) {
        if (combinations.isEmpty()) {
            return null;
        }
        List<Combination> ordered = new ArrayList<>(combinations);
        ordered.sort(comparator(mode, request));
        return ordered.get(0);
    }

    /** The comparator that defines each objective, exposed so the interface can explain it. */
    public Comparator<Combination> comparator(RecommendationMode mode, BudgetRequest request) {
        Comparator<Combination> tieBreak = Comparator.comparing(Combination::signature);
        long budgetCents = Math.max(1L, BudgetMoney.cents(request.budget()));
        return switch (mode) {
            case BEST_VALUE -> Comparator
                .comparingDouble(Combination::scoreSum).reversed()
                .thenComparingLong(Combination::totalCents)
                .thenComparingInt(Combination::itemCount)
                .thenComparing(tieBreak);
            case LOWEST_COST -> Comparator
                .comparingLong(Combination::totalCents)
                .thenComparing(Comparator.comparingDouble(Combination::scoreSum).reversed())
                .thenComparingInt(Combination::itemCount)
                .thenComparing(tieBreak);
            case BEST_SPECS -> Comparator
                .comparingDouble(Combination::specSum).reversed()
                .thenComparing(Comparator.comparingDouble(Combination::scoreSum).reversed())
                .thenComparingLong(Combination::totalCents)
                .thenComparing(tieBreak);
            case BALANCED -> Comparator
                .comparingDouble((Combination c) -> 0.6 * c.averageScore()
                    + 0.4 * (1.0 - (double) c.totalCents() / budgetCents)).reversed()
                .thenComparing(Comparator.comparingDouble(Combination::scoreSum).reversed())
                .thenComparingLong(Combination::totalCents)
                .thenComparing(tieBreak);
        };
    }

    /**
     * The quantity {@link RecommendationMode#BEST_VALUE} maximises: total weighted score per unit of
     * priority weight, i.e. scoreSum / Σ(quantity x category priority).
     *
     * <p>Distinct from {@code recommendation.averageScore()}, which ignores priority and quantity
     * weighting — a basket with more cheap items can have a lower plain average while still being the
     * better weighted choice.</p>
     */
    public static double weightedScore(BudgetRecommendation recommendation, BudgetRequest request) {
        double weight = 0;
        for (SelectedProduct line : recommendation.selected()) {
            BudgetRequest.CategoryRequirement requirement =
                request.byCategory().get(line.candidate().category());
            double factor = requirement == null || requirement.priority() == null
                ? 1.0 : requirement.priority().factor();
            weight += line.quantity() * factor;
        }
        return weight <= 0 ? recommendation.averageScore()
            : Math.min(1.0, recommendation.scoreSum() / weight);
    }

    /** Human readable statement of what a mode optimises, used in the results panel. */
    public static String objectiveStatement(RecommendationMode mode) {
        return switch (mode) {
            case BEST_VALUE -> "maximises the total weighted recommendation score, then prefers the cheaper basket";
            case LOWEST_COST -> "minimises the total cost, then prefers the higher score";
            case BEST_SPECS -> "maximises specification suitability, then the overall score, then prefers the cheaper basket";
            case BALANCED -> "maximises 0.6 x mean product score + 0.4 x share of the budget left unspent";
        };
    }

    private List<Option> options(BudgetRequest.CategoryRequirement requirement, List<ProductScore> scores,
                                 BudgetRequest request, long budgetCents) {
        List<Option> options = new ArrayList<>();
        double priority = requirement.priority() == null ? 1.0 : requirement.priority().factor();
        int min = requirement.effectiveMin();
        int max = requirement.effectiveMax();
        if (min == 0) {
            options.add(new Option(null, 0, 0L, 0.0, 0.0)); // optional category may be skipped
        }
        List<ProductScore> eligible = new ArrayList<>(scores);
        eligible.sort(Comparator.comparing((ProductScore s) -> s.candidate().productId())
            .thenComparing(s -> s.candidate().store()));
        for (ProductScore score : eligible) {
            if (!meetsMinimumSpec(score, requirement)) {
                continue;
            }
            long unitCents = BudgetMoney.cents(score.candidate().unitPrice());
            if (unitCents < 0) {
                continue;
            }
            double spec = score.component(ScoringCriterion.SPECIFICATIONS);
            for (int quantity = Math.max(1, min); quantity <= max; quantity++) {
                long lineCents = unitCents * quantity;
                if (lineCents > budgetCents) {
                    continue; // cannot fit even ignoring delivery
                }
                options.add(new Option(score, quantity, lineCents,
                    score.total() * quantity * priority, spec * quantity * priority));
            }
        }
        return options;
    }

    private boolean meetsMinimumSpec(ProductScore score, BudgetRequest.CategoryRequirement requirement) {
        if (requirement.minimumSpec() == null) {
            return true;
        }
        Double value = score.candidate().metrics().get(requirement.minimumSpec().metric());
        return value != null && value + 1e-9 >= requirement.minimumSpec().minimum();
    }

    private List<State> prune(List<State> states, List<String> storeOrder, Map<String, Long> feeCents,
                              BudgetRequest request, long budgetCents) {
        List<State> sorted = new ArrayList<>(states);
        sorted.sort(Comparator.comparingLong(State::itemsCents)
            .thenComparing(Comparator.comparingDouble(State::scoreSum).reversed())
            .thenComparing(Comparator.comparingDouble(State::specSum).reversed())
            .thenComparing(State::signature));

        List<State> kept = new ArrayList<>();
        List<double[]> frontier = new ArrayList<>(); // (score, spec) of kept states, all with lower cost
        Set<String> seen = new LinkedHashSet<>();
        for (State state : sorted) {
            if (calculator.lowerBoundCents(request, state.itemsCents, storesOf(state.mask, storeOrder), feeCents)
                > budgetCents) {
                continue;
            }
            boolean dominated = false;
            for (double[] point : frontier) {
                if (point[0] >= state.scoreSum - 1e-12 && point[1] >= state.specSum - 1e-12) {
                    dominated = true;
                    break;
                }
            }
            if (dominated) {
                continue;
            }
            if (!seen.add(state.signature())) {
                continue;
            }
            kept.add(state);
            // the new point dominates any frontier point that is no better on either objective
            frontier.removeIf(point -> point[0] <= state.scoreSum + 1e-12 && point[1] <= state.specSum + 1e-12);
            frontier.add(new double[]{state.scoreSum, state.specSum});
        }
        return kept;
    }

    private Set<String> storesOf(int mask, List<String> storeOrder) {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < storeOrder.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                out.add(storeOrder.get(i));
            }
        }
        return out;
    }

    private record Choice(Option option, String category, Choice prev) {
    }

    private record State(long itemsCents, int mask, double scoreSum, double specSum, Choice choices) {

        String signature() {
            List<String> parts = new ArrayList<>();
            for (Choice choice = choices; choice != null; choice = choice.prev) {
                parts.add(choice.option.signature());
            }
            parts.sort(String::compareTo);
            return String.format(Locale.ROOT, "%d|%d|", itemsCents, mask) + String.join(";", parts);
        }
    }
}
