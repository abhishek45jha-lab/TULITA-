package tulita.budget;

import org.junit.jupiter.api.Test;
import tulita.budget.model.*;
import tulita.budget.service.BudgetCatalogService;
import tulita.budget.service.BudgetOptimizer;
import tulita.dataset.model.DatasetProduct;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Money, budget limits, mandatory categories, quantity and mode behaviour. */
public class BudgetOptimizerTest {
    static DatasetProduct product(String id, String title, String category, String brand, String price, String rating) {
        return new DatasetProduct(id, id, title, category, "", brand, new BigDecimal(price), null,
                rating == null ? null : new BigDecimal(rating), "2023-01-01 10:00:00 +0000",
                "https://example.test/" + id, "description", "");
    }

    static List<DatasetProduct> catalogue() {
        return List.of(
                product("lap-1", "Dell Laptop Core i3 4GB RAM 256GB SSD", "Computers", "Dell", "25000", "4.0"),
                product("lap-2", "HP Laptop Core i5 8GB RAM 512GB SSD 15.6 inch", "Computers", "HP", "45000", "4.2"),
                product("lap-3", "Lenovo Laptop Core i7 16GB RAM 1TB SSD backlit", "Computers", "Lenovo", "80000", "4.5"),
                product("kb-1", "Logitech Wireless Keyboard", "Computers", "Logitech", "1200", "4.1"),
                product("kb-2", "Zebronics Mechanical Gaming Keyboard RGB backlit", "Computers", "Zebronics", "2500", "4.3"),
                product("mouse-1", "HP Wireless Mouse 1600 DPI", "Computers", "HP", "800", "4.2"),
                product("mouse-2", "Logitech Gaming Mouse 16000 DPI 6 buttons", "Computers", "Logitech", "3000", "4.6"),
                product("hp-1", "boAt Wired Headphone with mic", "Mobiles & Accessories", "boAt", "500", "4.0"),
                product("hp-2", "Sony Noise Cancelling Wireless Headphone 30 hours", "Mobiles & Accessories", "Sony", "15000", "4.4"),
                // Accessories and bundles must not become planning products.
                product("bad-1", "Dell Laptop Bag", "Computers", "Dell", "1500", "4.0"),
                product("bad-2", "Mickey Mouse Pencil Box", "Stationery", "Disney", "300", "4.0"),
                product("bad-3", "Casio Musical Keyboard Piano 61 key", "Musical Instruments", "Casio", "9000", "4.0"),
                product("bad-4", "Headphone Cushion Replacement", "Mobiles & Accessories", "Generic", "400", "4.0"),
                product("bad-5", "HP Keyboard and Mouse Combo", "Computers", "HP", "1999", "4.0"),
                product("bad-6", "Laptop Stand", "Computers", "Generic", "700", "4.0"),
                product("kb-1-dup", "Logitech Wireless Keyboard", "Computers", "Logitech", "1200", "4.1")
        );
    }

    static BudgetRequest request(String budget, PlanMode mode, Set<String> brands, CategoryRequirement... requirements) {
        return new BudgetRequest(new BigDecimal(budget), List.of(requirements), mode, brands);
    }

    static BudgetPlan plan(String budget, PlanMode mode, Set<String> brands, CategoryRequirement... requirements) {
        return new BudgetOptimizer().optimize(request(budget, mode, brands, requirements), new BudgetCatalogService(catalogue()));
    }

    @Test
    void catalogueClassifiesScoresAndDeduplicates() {
        BudgetCatalogService catalog = new BudgetCatalogService(catalogue());
        assertEquals(3, catalog.count(BudgetCategory.LAPTOPS));
        assertEquals(2, catalog.count(BudgetCategory.KEYBOARDS));
        assertEquals(2, catalog.count(BudgetCategory.MICE));
        assertEquals(2, catalog.count(BudgetCategory.HEADPHONES));
        assertEquals(1, catalog.duplicatesCollapsed());
        for (BudgetCategory category : BudgetCategory.values()) {
            for (ScoredProduct scored : catalog.products(category)) {
                assertTrue(scored.qualityScore() >= 0 && scored.qualityScore() <= 100);
                assertTrue(scored.valueScore() >= 0 && scored.valueScore() <= 100);
                assertTrue(scored.valueRank() >= 1 && scored.valueRank() <= scored.valueTotal());
                assertTrue(scored.product().price().signum() > 0);
            }
        }
    }

    @Test
    void bestValueStaysWithinBudgetAndCoversMandatoryCategories() {
        BudgetPlan plan = plan("50000", PlanMode.BEST_VALUE, Set.of("Logitech"),
                new CategoryRequirement(BudgetCategory.LAPTOPS, true, 1),
                new CategoryRequirement(BudgetCategory.KEYBOARDS, true, 1),
                new CategoryRequirement(BudgetCategory.MICE, false, 1),
                new CategoryRequirement(BudgetCategory.HEADPHONES, false, 1));
        assertTrue(plan.feasible());
        assertTrue(plan.optimal());
        assertTrue(plan.totalCost().compareTo(new BigDecimal("50000.00")) <= 0);
        assertTrue(plan.remaining().signum() >= 0);
        assertTrue(plan.utilizationPercent().compareTo(new BigDecimal("100")) <= 0);
        assertTrue(contains(plan, BudgetCategory.LAPTOPS));
        assertTrue(contains(plan, BudgetCategory.KEYBOARDS));
        assertEquals(plan.selections().stream().mapToInt(BudgetSelection::quantity).sum(), plan.itemCount());
        BigDecimal lineSum = BigDecimal.ZERO;
        for (BudgetSelection selection : plan.selections()) {
            assertFalse(selection.reasons().isEmpty());
            assertEquals(selection.product().price().multiply(BigDecimal.valueOf(selection.quantity())), selection.lineTotal());
            lineSum = lineSum.add(selection.lineTotal());
        }
        assertEquals(plan.totalCost(), lineSum);
        BigDecimal spendSum = plan.categorySpend().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(plan.totalCost(), spendSum);
        assertEquals(plan.totalCost().add(plan.remaining()), plan.budget());
    }

    @Test
    void optionalCategoryIsDroppedWhenItDoesNotFit() {
        BudgetPlan plan = plan("27000", PlanMode.BEST_VALUE, Set.of(),
                new CategoryRequirement(BudgetCategory.LAPTOPS, true, 1),
                new CategoryRequirement(BudgetCategory.KEYBOARDS, true, 1),
                new CategoryRequirement(BudgetCategory.MICE, false, 1),
                new CategoryRequirement(BudgetCategory.HEADPHONES, false, 1));
        assertTrue(plan.feasible());
        assertTrue(plan.totalCost().compareTo(new BigDecimal("27000.00")) <= 0);
        assertTrue(contains(plan, BudgetCategory.LAPTOPS));
        assertTrue(contains(plan, BudgetCategory.KEYBOARDS));
        assertFalse(contains(plan, BudgetCategory.HEADPHONES));
    }

    @Test
    void lowestCostUsesTheCheapestRecordedUnit() {
        BudgetPlan plan = plan("100000", PlanMode.LOWEST_COST, Set.of(),
                new CategoryRequirement(BudgetCategory.LAPTOPS, true, 1),
                new CategoryRequirement(BudgetCategory.KEYBOARDS, true, 1),
                new CategoryRequirement(BudgetCategory.MICE, false, 1),
                new CategoryRequirement(BudgetCategory.HEADPHONES, false, 1));
        assertTrue(plan.feasible());
        assertEquals(new BigDecimal("27500.00"), plan.totalCost());
        assertEquals(new BigDecimal("25000.00"), selection(plan, BudgetCategory.LAPTOPS).product().price());
        assertEquals(new BigDecimal("1200.00"), selection(plan, BudgetCategory.KEYBOARDS).product().price());
        assertEquals(new BigDecimal("800.00"), selection(plan, BudgetCategory.MICE).product().price());
        assertEquals(new BigDecimal("500.00"), selection(plan, BudgetCategory.HEADPHONES).product().price());
    }

    @Test
    void quantityUsesTheSameRecordedUnitPriceWithoutInventingDiscounts() {
        BudgetPlan plan = plan("1600", PlanMode.BEST_VALUE, Set.of(),
                new CategoryRequirement(BudgetCategory.MICE, true, 2));
        assertTrue(plan.feasible());
        assertEquals(new BigDecimal("1600.00"), plan.totalCost());
        assertEquals(2, plan.itemCount());
        assertEquals(1, plan.selections().size());
        assertEquals(2, plan.selections().get(0).quantity());
        assertEquals(new BigDecimal("800.00"), plan.selections().get(0).product().price());
        assertEquals(new BigDecimal("1600.00"), plan.selections().get(0).lineTotal());
    }

    @Test
    void quantityCanRepeatTheOnlyRecordedCandidate() {
        BudgetCatalogService single = new BudgetCatalogService(List.of(
                product("only-keyboard", "Logitech Wired Keyboard", "Computers", "Logitech", "1000", "4.0")));
        BudgetPlan plan = new BudgetOptimizer().optimize(
                request("3000", PlanMode.BEST_VALUE, Set.of(),
                        new CategoryRequirement(BudgetCategory.KEYBOARDS, true, 3)), single);
        assertTrue(plan.feasible());
        assertEquals(1, plan.selections().size());
        assertEquals(3, plan.selections().get(0).quantity());
        assertEquals(new BigDecimal("3000.00"), plan.totalCost());
    }

    @Test
    void requiredMinimumAboveBudgetIsInfeasibleNotSilentlyTrimmed() {
        BudgetPlan plan = plan("20000", PlanMode.BEST_VALUE, Set.of(),
                new CategoryRequirement(BudgetCategory.LAPTOPS, true, 1),
                new CategoryRequirement(BudgetCategory.MICE, true, 1));
        assertFalse(plan.feasible());
        assertEquals(new BigDecimal("0.00"), plan.totalCost());
        assertTrue(plan.selections().isEmpty());
        assertTrue(plan.warnings().stream().anyMatch(w -> w.contains("above your budget")));
    }

    @Test
    void invalidRequestsAreRejected() {
        BudgetCatalogService catalog = new BudgetCatalogService(catalogue());
        BudgetOptimizer optimizer = new BudgetOptimizer();
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(
                request("0", PlanMode.BEST_VALUE, Set.of(), new CategoryRequirement(BudgetCategory.MICE, true, 1)), catalog));
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(
                request("10000001", PlanMode.BEST_VALUE, Set.of(), new CategoryRequirement(BudgetCategory.MICE, true, 1)), catalog));
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(
                new BudgetRequest(new BigDecimal("1000"), List.of(), PlanMode.BEST_VALUE, Set.of()), catalog));
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(
                request("1000", PlanMode.BEST_VALUE, Set.of(),
                        new CategoryRequirement(BudgetCategory.MICE, true, 1),
                        new CategoryRequirement(BudgetCategory.MICE, false, 1)), catalog));
    }

    @Test
    void brandPreferenceChangesTheChoiceOnlyWithinTheBudget() {
        BudgetPlan without = plan("3000", PlanMode.BEST_VALUE, Set.of(),
                new CategoryRequirement(BudgetCategory.KEYBOARDS, true, 1));
        BudgetPlan with = plan("3000", PlanMode.BEST_VALUE, Set.of("Logitech"),
                new CategoryRequirement(BudgetCategory.KEYBOARDS, true, 1));
        assertEquals("Zebronics", selection(without, BudgetCategory.KEYBOARDS).product().brand());
        assertEquals("Logitech", selection(with, BudgetCategory.KEYBOARDS).product().brand());
        assertTrue(with.totalCost().compareTo(new BigDecimal("3000.00")) <= 0);
    }

    @Test
    void alternativesNeverExceedBudget() {
        BudgetPlan plan = plan("75000", PlanMode.BEST_VALUE, Set.of("HP"),
                new CategoryRequirement(BudgetCategory.LAPTOPS, true, 1),
                new CategoryRequirement(BudgetCategory.KEYBOARDS, false, 1),
                new CategoryRequirement(BudgetCategory.MICE, false, 1),
                new CategoryRequirement(BudgetCategory.HEADPHONES, false, 1));
        assertTrue(plan.feasible());
        for (BudgetAlternative alternative : plan.alternatives()) {
            assertTrue(alternative.totalCost().compareTo(plan.budget()) <= 0, alternative.label());
            assertFalse(alternative.selections().isEmpty());
            assertFalse(alternative.label().isBlank());
        }
    }

    private static boolean contains(BudgetPlan plan, BudgetCategory category) {
        return plan.selections().stream().anyMatch(s -> s.product().category() == category);
    }

    private static BudgetSelection selection(BudgetPlan plan, BudgetCategory category) {
        return plan.selections().stream().filter(s -> s.product().category() == category).findFirst()
                .orElseThrow(() -> new AssertionError("Missing category " + category));
    }
}
