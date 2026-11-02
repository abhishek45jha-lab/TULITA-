package tulita.budget.service;

import tulita.budget.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Budget optimizer for a multi-category basket.
 *
 * <p><b>Best Value</b> is a multiple-choice knapsack solved with depth-first branch-and-bound.
 * Every category chooses 0 units (only when optional) or {@code quantity} units (1–3), a mandatory
 * category is never dropped, and a partial solution is pruned when its optimistic utility cannot
 * beat the incumbent or when the cheapest completion already exceeds the budget. Single-unit
 * categories are Pareto-pruned on (price, utility), which is exact; quantities above one use a
 * bounded shortlist and disclose that the result is the best found rather than proven optimal.</p>
 *
 * <p><b>Lowest Cost</b> takes the cheapest recorded unit for the requested quantity in each
 * category. Categories are independent, so the sum of those category minima is the global minimum.
 * Optional categories are dropped only when the budget cannot cover everything.</p>
 *
 * <p>All money is {@link BigDecimal} in INR and every price comes from the catalogue. Quantity
 * multiplies the recorded unit price; no discount or bulk price is invented.</p>
 */
public final class BudgetOptimizer {
    public static final BigDecimal MAX_BUDGET = new BigDecimal("10000000");
    private static final int MAX_NODES = 750_000;
    private static final int MAX_COMBINATION_CANDIDATES = 24;
    private static final int MAX_ALTERNATIVES = 4;

    private int nodeCount;
    private boolean truncated;
    private int bestUtility;
    private BigDecimal bestCost;
    private String bestKey;
    private List<ScoredProduct> bestPicks;

    public BudgetPlan optimize(BudgetRequest request, BudgetCatalogService catalog) {
        Objects.requireNonNull(catalog, "catalog");
        EnumMap<BudgetCategory, List<ScoredProduct>> map = new EnumMap<>(BudgetCategory.class);
        for (BudgetCategory category : BudgetCategory.values()) map.put(category, catalog.products(category));
        return optimize(request, map);
    }

    public BudgetPlan optimize(BudgetRequest request, Map<BudgetCategory, List<ScoredProduct>> candidatesByCategory) {
        validate(request);
        Objects.requireNonNull(candidatesByCategory, "candidatesByCategory");
        BigDecimal budget = money(request.budget());
        Map<String, Integer> utilities = utilities(candidatesByCategory, request.preferredBrands());

        List<String> notes = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        for (CategoryRequirement requirement : request.requirements()) {
            List<ScoredProduct> available = new ArrayList<>(candidatesByCategory.getOrDefault(requirement.category(), List.of()));
            if (available.isEmpty()) {
                if (requirement.required()) {
                    return BudgetPlan.infeasible(request.mode(), budget, List.of(
                            "No recorded " + requirement.category().label().toLowerCase(Locale.ROOT)
                                    + " product is available in the current catalogue.",
                            "No product or price was invented to fill the category. Choose another mandatory category or load a complete catalogue."));
                }
                notes.add("Optional " + requirement.category().label().toLowerCase(Locale.ROOT)
                        + " was left out: no recorded candidate exists.");
                continue;
            }
            // Quantity may repeat the same recorded product, so one candidate can support all units.
            nodes.add(new Node(requirement, available));
        }
        if (nodes.isEmpty()) {
            return BudgetPlan.infeasible(request.mode(), budget, List.of(
                    "None of the selected categories has a matching priced product in the current INR catalogue.",
                    "The bundled historical catalogue is limited to the rows shipped with Tulita; nothing was fabricated."));
        }

        BigDecimal requiredMinimum = BigDecimal.ZERO;
        List<String> minimumLines = new ArrayList<>();
        for (Node node : nodes) {
            if (!node.requirement.required()) continue;
            BigDecimal subtotal = sum(cheapestPicks(node.candidates, node.requirement.quantity(), utilities));
            requiredMinimum = requiredMinimum.add(subtotal);
            minimumLines.add(node.requirement.category().label() + " " + format(subtotal));
        }
        if (requiredMinimum.compareTo(budget) > 0) {
            List<String> reasons = new ArrayList<>();
            reasons.add("The cheapest possible basket for the mandatory categories costs " + format(requiredMinimum)
                    + ", which is " + format(requiredMinimum.subtract(budget)) + " above your budget.");
            reasons.add("Mandatory minimums: " + String.join(" · ", minimumLines) + ".");
            reasons.add("Raise the budget, remove a mandatory category, or mark it optional.");
            return BudgetPlan.infeasible(request.mode(), budget, reasons);
        }

        BudgetPlan base = solve(request, budget, nodes, utilities, notes);
        if (!base.feasible()) return base;
        List<BudgetAlternative> alternatives = alternatives(request, base, nodes, utilities, budget);
        return new BudgetPlan(base.mode(), base.budget(), base.selections(), base.totalCost(), base.remaining(),
                base.utilizationPercent(), base.categorySpend(), true, base.optimal(), base.utility(),
                base.warnings(), base.explanations(), alternatives);
    }

    private void validate(BudgetRequest request) {
        if (request == null) throw new IllegalArgumentException("A budget request is required.");
        if (request.budget() == null) throw new IllegalArgumentException("Enter a budget in INR.");
        BigDecimal rounded = money(request.budget());
        if (rounded.signum() <= 0) throw new IllegalArgumentException("Budget must be greater than ₹0.");
        if (request.budget().compareTo(MAX_BUDGET) > 0)
            throw new IllegalArgumentException("Budget is limited to " + format(MAX_BUDGET) + " to guard against typing mistakes.");
        if (request.mode() == null) throw new IllegalArgumentException("Choose Best Value or Lowest Cost.");
        if (request.requirements().isEmpty()) throw new IllegalArgumentException("Select at least one category.");
        Set<BudgetCategory> seen = EnumSet.noneOf(BudgetCategory.class);
        for (CategoryRequirement requirement : request.requirements()) {
            if (requirement == null) throw new IllegalArgumentException("A category requirement is missing.");
            if (!seen.add(requirement.category()))
                throw new IllegalArgumentException("Each category can be selected only once.");
        }
    }

    private BudgetPlan solve(BudgetRequest request, BigDecimal budget, List<Node> nodes,
                             Map<String, Integer> utilities, List<String> notes) {
        return request.mode() == PlanMode.LOWEST_COST
                ? lowestCost(request, budget, nodes, utilities, notes)
                : bestValue(request, budget, nodes, utilities, notes);
    }

    private BudgetPlan bestValue(BudgetRequest request, BigDecimal budget, List<Node> nodes,
                                 Map<String, Integer> utilities, List<String> initialNotes) {
        List<Node> prepared = new ArrayList<>();
        boolean shortlisted = false;
        for (Node node : nodes) {
            int original = node.candidates.size();
            List<ScoredProduct> candidates = node.requirement.quantity() == 1
                    ? pareto(node.candidates, utilities)
                    : capped(node.candidates, utilities);
            if (candidates.size() < original) shortlisted = shortlisted || node.requirement.quantity() > 1;
            prepared.add(new Node(node.requirement, candidates));
        }

        int n = prepared.size();
        BigDecimal[] suffixMin = new BigDecimal[n + 1];
        int[] suffixMaxUtility = new int[n + 1];
        suffixMin[n] = BigDecimal.ZERO;
        suffixMaxUtility[n] = 0;
        for (int i = n - 1; i >= 0; i--) {
            Node node = prepared.get(i);
            BigDecimal min = BigDecimal.ZERO;
            if (node.requirement.required())
                min = sum(cheapestPicks(node.candidates, node.requirement.quantity(), utilities));
            int maxUnit = node.candidates.stream()
                    .mapToInt(p -> utilities.getOrDefault(p.product().recordId(), 0)).max().orElse(0);
            suffixMin[i] = suffixMin[i + 1].add(min);
            suffixMaxUtility[i] = suffixMaxUtility[i + 1] + maxUnit * node.requirement.quantity();
        }

        this.nodeCount = 0;
        this.truncated = false;
        this.bestUtility = Integer.MIN_VALUE;
        this.bestCost = null;
        this.bestKey = null;
        this.bestPicks = new ArrayList<>();

        search(0, new ArrayList<>(), BigDecimal.ZERO, 0, budget, prepared, suffixMin, suffixMaxUtility, utilities);

        List<ScoredProduct> picks = new ArrayList<>(bestPicks);
        List<String> warnings = new ArrayList<>(initialNotes);
        if (picks.isEmpty()) {
            warnings.add("No optional category fits this budget. Add a mandatory category or raise the budget.");
            return BudgetPlan.infeasible(request.mode(), budget, warnings);
        }
        for (Node node : prepared) {
            if (!node.requirement.required()
                    && picks.stream().noneMatch(p -> p.product().category() == node.requirement.category())) {
                warnings.add("Optional " + node.requirement.category().label().toLowerCase(Locale.ROOT)
                        + " was not included: no affordable combination improved the selected plan.");
            }
        }
        if (truncated) warnings.add("The search reached its safety limit; this is the best plan found, not a proof of optimality.");
        if (shortlisted) warnings.add("Quantity above one uses a bounded shortlist per category; the plan is the best found for that shortlist.");
        return plan(request, budget, picks, utilities, warnings, !truncated && !shortlisted);
    }

    private void search(int index, List<ScoredProduct> chosen, BigDecimal cost, int utility, BigDecimal budget,
                        List<Node> nodes, BigDecimal[] suffixMin, int[] suffixMaxUtility,
                        Map<String, Integer> utilities) {
        if (truncated) return;
        if (++nodeCount > MAX_NODES) {
            truncated = true;
            return;
        }
        if (cost.add(suffixMin[index]).compareTo(budget) > 0) return;
        if (utility + suffixMaxUtility[index] < bestUtility) return;

        if (index == nodes.size()) {
            if (cost.compareTo(budget) > 0) return;
            String key = key(chosen);
            boolean better = utility > bestUtility
                    || (utility == bestUtility && (bestCost == null || cost.compareTo(bestCost) < 0))
                    || (utility == bestUtility && bestCost != null && cost.compareTo(bestCost) == 0
                    && (bestKey == null || key.compareTo(bestKey) < 0));
            if (better) {
                bestUtility = utility;
                bestCost = cost;
                bestKey = key;
                bestPicks = new ArrayList<>(chosen);
            }
            return;
        }

        Node node = nodes.get(index);
        chooseCombinations(node.candidates, node.requirement.quantity(), 0, new ArrayList<>(), (picks, subtotal, gain) -> {
            if (cost.add(subtotal).add(suffixMin[index + 1]).compareTo(budget) > 0) return;
            List<ScoredProduct> next = new ArrayList<>(chosen);
            next.addAll(picks);
            search(index + 1, next, cost.add(subtotal), utility + gain, budget, nodes, suffixMin, suffixMaxUtility, utilities);
        }, utilities);

        if (!node.requirement.required()) {
            search(index + 1, chosen, cost, utility, budget, nodes, suffixMin, suffixMaxUtility, utilities);
        }
    }

    private interface CombinationVisitor {
        void visit(List<ScoredProduct> picks, BigDecimal subtotal, int utility);
    }

    private void chooseCombinations(List<ScoredProduct> candidates, int quantity, int start, List<ScoredProduct> picks,
                                    CombinationVisitor visitor, Map<String, Integer> utilities) {
        if (picks.size() == quantity) {
            BigDecimal subtotal = sum(picks);
            int gain = picks.stream().mapToInt(p -> utilities.getOrDefault(p.product().recordId(), 0)).sum();
            visitor.visit(List.copyOf(picks), subtotal, gain);
            return;
        }
        // start at i (not i+1) so the same recorded product can cover several units.
        for (int i = start; i < candidates.size(); i++) {
            ScoredProduct candidate = candidates.get(i);
            picks.add(candidate);
            chooseCombinations(candidates, quantity, i, picks, visitor, utilities);
            picks.remove(picks.size() - 1);
        }
    }

    private BudgetPlan lowestCost(BudgetRequest request, BigDecimal budget, List<Node> nodes,
                                  Map<String, Integer> utilities, List<String> initialNotes) {
        List<String> warnings = new ArrayList<>(initialNotes);
        List<ScoredProduct> picks = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        EnumMap<BudgetCategory, BigDecimal> cheapestTotal = new EnumMap<>(BudgetCategory.class);
        for (Node node : nodes) {
            List<ScoredProduct> selected = cheapestPicks(node.candidates, node.requirement.quantity(), utilities);
            cheapestTotal.put(node.requirement.category(), sum(selected));
            picks.addAll(selected);
            total = total.add(sum(selected));
        }
        List<Node> optional = new ArrayList<>(nodes.stream().filter(n -> !n.requirement.required()).toList());
        optional.sort(Comparator.comparing((Node n) -> cheapestTotal.get(n.requirement.category())).reversed());
        for (Node node : optional) {
            if (total.compareTo(budget) <= 0) break;
            List<ScoredProduct> selected = cheapestPicks(node.candidates, node.requirement.quantity(), utilities);
            picks.removeAll(selected);
            total = total.subtract(sum(selected));
            warnings.add("Optional " + node.requirement.category().label().toLowerCase(Locale.ROOT)
                    + " was left out because the cheapest option did not fit the budget.");
        }
        if (total.compareTo(budget) > 0) return BudgetPlan.infeasible(request.mode(), budget, warnings);
        if (picks.isEmpty()) {
            warnings.add("No optional category fits this budget. Add a mandatory category or raise the budget.");
            return BudgetPlan.infeasible(request.mode(), budget, warnings);
        }
        return plan(request, budget, picks, utilities, warnings, true);
    }

    private BudgetPlan plan(BudgetRequest request, BigDecimal budget, List<ScoredProduct> picks,
                            Map<String, Integer> utilities, List<String> warnings, boolean optimal) {
        List<BudgetSelection> selections = selectionsFor(request, picks, utilities);
        BigDecimal total = sum(picks);
        if (total.compareTo(budget) > 0) {
            return BudgetPlan.infeasible(request.mode(), budget, List.of(
                    "The calculated basket exceeded the budget and was rejected by the safety check.",
                    "No partial or over-budget plan is shown."));
        }
        EnumMap<BudgetCategory, BigDecimal> spend = new EnumMap<>(BudgetCategory.class);
        for (BudgetSelection selection : selections) {
            spend.merge(selection.product().category(), selection.lineTotal(), BigDecimal::add);
        }
        BigDecimal remaining = budget.subtract(total);
        int utility = selections.stream().mapToInt(BudgetSelection::lineUtility).sum();

        List<String> notes = new ArrayList<>(warnings);
        if (selections.stream().anyMatch(s -> s.quantity() > 1))
            notes.add("Quantities use the same recorded unit price; no bulk discount is assumed.");
        if (picks.stream().anyMatch(p -> p.product().renewed()))
            notes.add("A renewed/refurbished item is included; the condition is stated in the source title.");
        if (picks.stream().anyMatch(p -> p.product().rating() == null))
            notes.add("Some selected records have no source rating. No rating was invented.");
        notes.add("Recorded item prices only: delivery, tax, condition guarantees and current availability are unknown.");

        List<String> explanations = new ArrayList<>();
        if (request.mode() == PlanMode.BEST_VALUE) {
            explanations.add("Best Value maximises a transparent 0–100 utility per unit: 65% specification quality, "
                    + "20% quality-per-rupee value and 15% brand preference. Mandatory categories are never dropped.");
        } else {
            explanations.add("Lowest Cost takes the cheapest recorded product(s) that satisfy every mandatory category. "
                    + "Optional categories are included only when their cheapest option still fits.");
        }
        explanations.add("Total " + format(total) + " of " + format(budget) + " · remaining " + format(remaining)
                + " · utilisation " + BudgetPlan.utilization(total, budget).toPlainString() + "%.");
        if (selections.stream().anyMatch(s -> s.quantity() > 1))
            explanations.add("Line totals multiply the recorded unit price by quantity; no discount is claimed.");
        if (!request.preferredBrands().isEmpty()) {
            long matched = selections.stream().filter(s -> request.preferredBrands().contains(s.product().brand())).count();
            explanations.add(matched == 0
                    ? "None of the preferred brands appears in this catalogue's selected categories; the plan uses the best available records."
                    : matched + " selected line(s) match your preferred brand list.");
        }
        return new BudgetPlan(request.mode(), budget, selections, BudgetPlan.money(total),
                BudgetPlan.money(remaining), BudgetPlan.utilization(total, budget), spend, true, optimal, utility,
                notes, explanations, List.of());
    }

    private List<BudgetSelection> selectionsFor(BudgetRequest request, List<ScoredProduct> picks,
                                                Map<String, Integer> utilities) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        Map<String, ScoredProduct> byId = new HashMap<>();
        for (ScoredProduct pick : picks) {
            counts.merge(pick.product().recordId(), 1, Integer::sum);
            byId.putIfAbsent(pick.product().recordId(), pick);
        }
        List<ScoredProduct> ordered = new ArrayList<>(byId.values());
        ordered.sort(Comparator.comparing((ScoredProduct p) -> p.product().category().ordinal())
                .thenComparing(p -> p.product().title()));
        List<BudgetSelection> selections = new ArrayList<>();
        for (ScoredProduct pick : ordered) {
            int quantity = counts.get(pick.product().recordId());
            int unitUtility = utilities.getOrDefault(pick.product().recordId(), 0);
            selections.add(new BudgetSelection(pick, quantity, unitUtility, reasons(request, pick, quantity), List.of()));
        }
        return selections;
    }

    private List<String> reasons(BudgetRequest request, ScoredProduct pick, int quantity) {
        List<String> reasons = new ArrayList<>();
        if (request.mode() == PlanMode.LOWEST_COST) {
            reasons.add("Lowest recorded unit price meeting this category requirement.");
        } else {
            reasons.add("Best overall fit for the budget within the " + pick.product().category().label().toLowerCase(Locale.ROOT) + " shortlist.");
        }
        if (quantity > 1) {
            reasons.add("Quantity " + quantity + " × " + format(pick.product().price()) + " = "
                    + format(pick.product().price().multiply(BigDecimal.valueOf(quantity)))
                    + " at the recorded unit price (no invented bulk discount).");
        }
        if (request.preferredBrands().contains(pick.product().brand()))
            reasons.add("Preferred brand: " + pick.product().brand() + ".");
        String factors = pick.factors().stream().filter(f -> f.points() > 0)
                .sorted(Comparator.comparingInt(ScoreFactor::points).reversed()).limit(3)
                .map(ScoreFactor::describe).reduce((a, b) -> a + "; " + b).orElse("");
        reasons.add("Quality " + pick.qualityScore() + "/100"
                + (factors.isBlank() ? " — only limited specifications were supplied." : " — " + factors + "."));
        reasons.add("Value " + pick.valueScore() + "/100 · " + pick.valueNote() + " on quality per recorded rupee.");
        boolean ratingFactor = pick.factors().stream().anyMatch(f -> f.label().toLowerCase(Locale.ROOT).contains("rating") && f.points() > 0);
        if (pick.product().rating() != null && !ratingFactor)
            reasons.add("Source rating " + pick.product().rating().stripTrailingZeros().toPlainString() + "/5.");
        if (pick.product().renewed()) reasons.add("Renewed/refurbished condition is stated in the source title.");
        reasons.add("Recorded at " + pick.product().store() + " · " + pick.product().observedLabel() + " · historical price, not live.");
        return reasons;
    }

    private List<BudgetAlternative> alternatives(BudgetRequest request, BudgetPlan base, List<Node> nodes,
                                                 Map<String, Integer> utilities, BigDecimal budget) {
        List<BudgetAlternative> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(keySelections(base.selections()));

        PlanMode other = request.mode() == PlanMode.BEST_VALUE ? PlanMode.LOWEST_COST : PlanMode.BEST_VALUE;
        BudgetRequest otherRequest = new BudgetRequest(request.budget(), request.requirements(), other, request.preferredBrands());
        BudgetPlan otherPlan = solve(otherRequest, budget, nodes, utilities, new ArrayList<>());
        if (otherPlan.feasible() && !otherPlan.selections().isEmpty() && seen.add(keySelections(otherPlan.selections()))) {
            result.add(new BudgetAlternative(other.label() + " plan",
                    "Complete " + other.label() + " plan · " + format(otherPlan.totalCost()) + " · utility " + otherPlan.utility(),
                    otherPlan.mode(), otherPlan.selections(), otherPlan.totalCost(), otherPlan.utility(),
                    BudgetPlan.money(otherPlan.totalCost().subtract(base.totalCost())),
                    otherPlan.utility() - base.utility()));
        }

        List<ScoredProduct> basePicks = new ArrayList<>();
        for (BudgetSelection selection : base.selections())
            basePicks.addAll(Collections.nCopies(selection.quantity(), selection.scored()));

        for (BudgetSelection selection : base.selections()) {
            List<ScoredProduct> replacements = new ArrayList<>();
            for (Node node : nodes) {
                if (node.requirement.category() != selection.product().category()) continue;
                for (ScoredProduct candidate : node.candidates) {
                    if (!candidate.product().recordId().equals(selection.product().recordId())) replacements.add(candidate);
                }
            }
            replacements.sort(request.mode() == PlanMode.BEST_VALUE
                    ? Comparator.comparingInt((ScoredProduct p) -> utilities.getOrDefault(p.product().recordId(), 0)).reversed()
                    : Comparator.comparing(p -> p.product().price()));
            int taken = 0;
            for (ScoredProduct replacement : replacements) {
                if (taken >= 2) break;
                BigDecimal total = base.totalCost().subtract(selection.lineTotal())
                        .add(replacement.product().price().multiply(BigDecimal.valueOf(selection.quantity())));
                if (total.compareTo(budget) > 0) continue;
                int candidateUnitUtility = utilities.getOrDefault(replacement.product().recordId(), 0);
                if (candidateUnitUtility <= selection.utility() && total.compareTo(base.totalCost()) >= 0) continue;

                List<ScoredProduct> swapped = new ArrayList<>(basePicks);
                int removed = 0;
                Iterator<ScoredProduct> iterator = swapped.iterator();
                while (iterator.hasNext() && removed < selection.quantity()) {
                    if (iterator.next().product().recordId().equals(selection.product().recordId())) {
                        iterator.remove();
                        removed++;
                    }
                }
                swapped.addAll(Collections.nCopies(selection.quantity(), replacement));
                String key = key(swapped);
                if (!seen.add(key)) continue;
                List<BudgetSelection> selections = selectionsFor(request, swapped, utilities);
                int newUtility = base.utility() - selection.lineUtility() + candidateUnitUtility * selection.quantity();
                String label = "Swap " + selection.quantity() + " × " + brief(selection.product().title())
                        + " for " + brief(replacement.product().title());
                String description = format(base.totalCost()) + " → " + format(total)
                        + " · utility " + base.utility() + " → " + newUtility;
                result.add(new BudgetAlternative(label, description, request.mode(), selections, BudgetPlan.money(total),
                        newUtility, BudgetPlan.money(total.subtract(base.totalCost())), newUtility - base.utility()));
                taken++;
            }
        }

        result.sort(request.mode() == PlanMode.BEST_VALUE
                ? Comparator.comparingInt(BudgetAlternative::utility).reversed()
                .thenComparing(BudgetAlternative::totalCost)
                : Comparator.comparing(BudgetAlternative::totalCost)
                .thenComparing(Comparator.comparingInt(BudgetAlternative::utility).reversed()));
        return result.size() <= MAX_ALTERNATIVES ? List.copyOf(result) : List.copyOf(result.subList(0, MAX_ALTERNATIVES));
    }

    private List<ScoredProduct> pareto(List<ScoredProduct> candidates, Map<String, Integer> utilities) {
        List<ScoredProduct> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing((ScoredProduct p) -> p.product().price())
                .thenComparing(p -> p.product().recordId()));
        List<ScoredProduct> kept = new ArrayList<>();
        int bestUtility = Integer.MIN_VALUE;
        for (ScoredProduct candidate : sorted) {
            int utility = utilities.getOrDefault(candidate.product().recordId(), 0);
            if (utility > bestUtility) {
                kept.add(candidate);
                bestUtility = utility;
            }
        }
        return kept;
    }

    private List<ScoredProduct> capped(List<ScoredProduct> candidates, Map<String, Integer> utilities) {
        LinkedHashMap<String, ScoredProduct> unique = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparingInt((ScoredProduct p) -> utilities.getOrDefault(p.product().recordId(), 0)).reversed())
                .limit(MAX_COMBINATION_CANDIDATES)
                .forEach(p -> unique.put(p.product().recordId(), p));
        candidates.stream().sorted(Comparator.comparing(p -> p.product().price()))
                .limit(6).forEach(p -> unique.putIfAbsent(p.product().recordId(), p));
        List<ScoredProduct> out = new ArrayList<>(unique.values());
        out.sort(Comparator.comparingInt((ScoredProduct p) -> utilities.getOrDefault(p.product().recordId(), 0)).reversed()
                .thenComparing(p -> p.product().price()));
        return out;
    }

    private List<ScoredProduct> cheapestPicks(List<ScoredProduct> candidates, int quantity, Map<String, Integer> utilities) {
        ScoredProduct cheapest = candidates.stream()
                .min(Comparator.comparing((ScoredProduct p) -> p.product().price())
                        .thenComparing(Comparator.comparingInt((ScoredProduct p) -> utilities.getOrDefault(p.product().recordId(), 0)).reversed())
                        .thenComparing(p -> p.product().recordId()))
                .orElseThrow(() -> new IllegalArgumentException("A category has no candidates."));
        return Collections.nCopies(quantity, cheapest);
    }

    private Map<String, Integer> utilities(Map<BudgetCategory, List<ScoredProduct>> map, Set<String> preferred) {
        Map<String, Integer> utilities = new HashMap<>();
        for (List<ScoredProduct> rows : map.values()) {
            for (ScoredProduct row : rows) {
                int brand = preferred.contains(row.product().brand()) ? 100 : 50;
                int utility = (int) Math.round(0.65 * row.qualityScore() + 0.20 * row.valueScore() + 0.15 * brand);
                utilities.put(row.product().recordId(), Math.max(0, Math.min(100, utility)));
            }
        }
        return utilities;
    }

    private static String key(List<ScoredProduct> picks) {
        return picks.stream().map(p -> p.product().recordId()).sorted().reduce((a, b) -> a + "|" + b).orElse("");
    }

    private static String keySelections(List<BudgetSelection> selections) {
        List<String> ids = new ArrayList<>();
        for (BudgetSelection selection : selections)
            for (int i = 0; i < selection.quantity(); i++) ids.add(selection.product().recordId());
        ids.sort(String::compareTo);
        return String.join("|", ids);
    }

    private static BigDecimal sum(List<ScoredProduct> picks) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScoredProduct pick : picks) total = total.add(pick.product().price());
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String format(BigDecimal value) {
        return tulita.util.Money.inr(value);
    }

    private static String brief(String title) {
        return title.length() <= 46 ? title : title.substring(0, 44) + "…";
    }

    private record Node(CategoryRequirement requirement, List<ScoredProduct> candidates) {
        Node {
            candidates = List.copyOf(candidates);
        }
    }
}
