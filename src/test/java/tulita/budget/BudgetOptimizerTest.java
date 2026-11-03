package tulita.budget;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tulita.budget.dao.JsonBudgetHistoryDao;
import tulita.budget.engine.BudgetOptimizationEngine;
import tulita.budget.engine.OptimizationConstraintValidator;
import tulita.budget.model.LineCharges;
import tulita.budget.model.RecommendationDelta;
import tulita.budget.model.WhatIfOption;
import tulita.budget.model.BudgetRecommendation;
import tulita.budget.model.BudgetRequest;
import tulita.budget.model.OptimizationResult;
import tulita.budget.model.PreferenceWeights;
import tulita.budget.model.ProductScore;
import tulita.budget.model.RecommendationAlternative;
import tulita.budget.model.RecommendationMode;
import tulita.budget.model.ScoringCriterion;
import tulita.budget.model.SelectedProduct;
import tulita.budget.service.BudgetCalculationService;
import tulita.budget.service.BudgetMoney;
import tulita.budget.service.BudgetOptimizerService;
import tulita.budget.service.CurrencyConverter;
import tulita.photocatalog.model.PhotoProduct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the budget planner. Every scenario listed in the project brief has one test here, plus
 * integration against the real curated catalogue and the CSV export.
 */
class BudgetOptimizerTest {

    private final BudgetOptimizerService service = new BudgetOptimizerService();

    // ------------------------------------------------------------------ fixtures
    private static PhotoProduct product(String id, String category, String brand, String store, String price,
                                        String specsJson) {
        PhotoProduct.Offer offer = new PhotoProduct.Offer(store,
            price == null ? null : new BigDecimal(price), "USD", "Title of " + id, "https://example.invalid/" + id,
            "", "test.csv", 1, 1, "0".repeat(64), specsJson, "description");
        return new PhotoProduct(id, "Product " + id, brand, "M" + id, "single unit", category, "test fixture",
            List.of(offer), List.of());
    }

    private static PhotoProduct productAt(String id, String category, String brand, String store, String price,
                                          String specsJson, int row) {
        PhotoProduct.Offer offer = new PhotoProduct.Offer(store,
            price == null ? null : new BigDecimal(price), "USD", "Title of " + id, "https://example.invalid/" + id,
            "", "test.csv", row, 1, "0".repeat(64), specsJson, "description");
        return new PhotoProduct(id, "Product " + id, brand, "M" + id, "single unit", category, "test fixture",
            List.of(offer), List.of());
    }

    /** Same fixture, but priced in another currency — used to prove the planner is currency-agnostic. */
    private static PhotoProduct productIn(String currency, String id, String category, String brand,
                                          String store, String price, String specsJson) {
        PhotoProduct.Offer offer = new PhotoProduct.Offer(store,
            price == null ? null : new BigDecimal(price), currency, "Title of " + id,
            "https://example.invalid/" + id, "", "test.csv", 1, 1, "0".repeat(64), specsJson, "description");
        return new PhotoProduct(id, "Product " + id, brand, "M" + id, "single unit", category,
            "test fixture", List.of(offer), List.of());
    }

    private static String specs(String... keyValues) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"key\":\"").append(keyValues[i]).append("\",\"value\":\"")
                .append(keyValues[i + 1]).append("\"}");
        }
        return out.append(']').toString();
    }

    private static BudgetRequest.CategoryRequirement required(String category, int quantity) {
        return new BudgetRequest.CategoryRequirement(category, true, quantity, quantity, null,
            BudgetRequest.Priority.MEDIUM, null, null);
    }

    private static BudgetRequest request(String budget, List<BudgetRequest.CategoryRequirement> categories,
                                         RecommendationMode mode) {
        return BudgetRequest.of(new BigDecimal(budget), "USD", categories,
            tulita.budget.model.PreferenceWeights.DEFAULT, mode);
    }

    // ------------------------------------------------------------------ 1. valid budget
    @Test
    @DisplayName("1. a feasible request returns a recommendation inside the budget")
    void validBudgetProducesRecommendation() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Computers", "Acme", "Amazon", "300.00", specs("Ram Size", "8 GB")),
            product("b", "Computers", "Acme", "Walmart", "500.00", specs("Ram Size", "16 GB")));
        OptimizationResult result = service.optimize(catalogue,
            request("1000.00", List.of(required("Computers", 1)), RecommendationMode.BEST_VALUE));

        assertTrue(result.feasible());
        assertNotNull(result.primary());
        assertTrue(result.primary().breakdown().withinBudget());
        assertEquals(1, result.primary().itemCount());
        assertTrue(result.primary().breakdown().total().compareTo(new BigDecimal("1000.00")) <= 0);
        assertEquals(List.of("Computers"), result.primary().satisfiedCategories());
        assertFalse(result.primary().headline().isBlank());
        assertTrue(result.algorithmNote().contains("Pareto"));
    }

    // ------------------------------------------------------------------ 2. insufficient budget
    @Test
    @DisplayName("2. an impossible budget is reported, never relaxed")
    void insufficientBudgetIsReported() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Computers", "Acme", "Amazon", "300.00", specs("Ram Size", "8 GB")));
        OptimizationResult result = service.optimize(catalogue,
            request("100.00", List.of(required("Computers", 1)), RecommendationMode.LOWEST_COST));

        assertFalse(result.feasible());
        assertNull(result.primary());
        assertFalse(result.infeasibleReasons().isEmpty());
        assertFalse(result.suggestions().isEmpty());
        assertTrue(result.suggestions().stream().anyMatch(s -> s.toLowerCase().contains("raise the budget")));
        assertTrue(result.alternatives().isEmpty());
    }

    // ------------------------------------------------------------------ 3. both objectives
    @Test
    @DisplayName("3. Best Value and Lowest Cost optimise different objectives")
    void bestValueAndLowestCostDiffer() {
        List<PhotoProduct> catalogue = List.of(
            product("cheap", "Computers", "Acme", "Amazon", "100.00", specs("Ram Size", "4 GB")),
            product("strong", "Computers", "Acme", "Amazon", "400.00", specs("Ram Size", "16 GB")));
        var categories = List.of(required("Computers", 1));

        OptimizationResult priceFirst = service.optimize(catalogue,
            request("500.00", categories, RecommendationMode.LOWEST_COST));
        OptimizationResult valueFirst = service.optimize(catalogue,
            request("500.00", categories, RecommendationMode.BEST_VALUE));

        assertTrue(priceFirst.feasible() && valueFirst.feasible());
        assertEquals("cheap", priceFirst.primary().selected().get(0).candidate().productId());
        assertEquals("strong", valueFirst.primary().selected().get(0).candidate().productId());
        assertNotEquals(priceFirst.primary().signature(), valueFirst.primary().signature());
        // both are valid plans; they simply optimise different things
        assertTrue(valueFirst.primary().averageScore() > priceFirst.primary().averageScore());
        assertTrue(priceFirst.primary().breakdown().total()
            .compareTo(valueFirst.primary().breakdown().total()) < 0);
    }

    // ------------------------------------------------------------------ 4. missing price
    @Test
    @DisplayName("4. a product without a usable price can never enter a basket")
    void missingPriceIsExcluded() {
        List<PhotoProduct> catalogue = List.of(
            product("free-of-price", "Computers", "Acme", "Amazon", null, specs("Ram Size", "8 GB")),
            product("priced", "Computers", "Acme", "Walmart", "250.00", specs("Ram Size", "8 GB")));

        OptimizationResult result = service.optimize(catalogue,
            request("400.00", List.of(required("Computers", 1)), RecommendationMode.LOWEST_COST));
        assertTrue(result.feasible());
        assertEquals("priced", result.primary().selected().get(0).candidate().productId());

        OptimizationResult onlyUnpriced = service.optimize(
            List.of(product("free-of-price", "Computers", "Acme", "Amazon", null, specs())),
            request("400.00", List.of(required("Computers", 1)), RecommendationMode.BEST_VALUE));
        assertFalse(onlyUnpriced.feasible());
        assertTrue(onlyUnpriced.infeasibleReasons().toString().contains("Computers"));
    }

    // ------------------------------------------------------------------ 5. optional categories
    @Test
    @DisplayName("5. optional categories may be omitted and never break a mandatory rule")
    void optionalCategoriesAreSkipped() {
        List<PhotoProduct> catalogue = List.of(
            product("laptop", "Computers", "Acme", "Amazon", "400.00", specs("Ram Size", "8 GB")),
            product("mouse", "Electronics", "Acme", "Amazon", "120.00", specs()));
        List<BudgetRequest.CategoryRequirement> categories = List.of(
            required("Computers", 1),
            new BudgetRequest.CategoryRequirement("Electronics", false, 0, 1, null,
                BudgetRequest.Priority.LOW, null, null));

        OptimizationResult tight = service.optimize(catalogue, request("450.00", categories,
            RecommendationMode.LOWEST_COST));
        assertTrue(tight.feasible());
        assertEquals(1, tight.primary().selected().size());
        assertEquals("Computers", tight.primary().selected().get(0).candidate().category());

        OptimizationResult roomy = service.optimize(catalogue, request("900.00", categories,
            RecommendationMode.BEST_VALUE));
        assertTrue(roomy.feasible());
        assertEquals(2, roomy.primary().selected().size(), "optional category fits, so it is included");
    }

    // ------------------------------------------------------------------ 6. quantities
    @Test
    @DisplayName("6. quantities are multiplied into the line and the total")
    void quantitiesAreMultiplied() {
        List<PhotoProduct> catalogue = List.of(
            product("cable", "Electronics", "Acme", "Amazon", "100.00", specs()));
        OptimizationResult result = service.optimize(catalogue,
            request("1000.00", List.of(required("Electronics", 3)), RecommendationMode.LOWEST_COST));

        assertTrue(result.feasible());
        SelectedProduct line = result.primary().selected().get(0);
        assertEquals(3, line.quantity());
        assertEquals(0, line.lineTotal().compareTo(new BigDecimal("300.00")));
        assertEquals(3, result.primary().itemCount());
        assertEquals(0, result.primary().breakdown().itemsSubtotal().compareTo(new BigDecimal("300.00")));
    }

    // ------------------------------------------------------------------ 7. delivery + discount
    @Test
    @DisplayName("7. delivery is charged once per retailer and discounts respect their threshold")
    void deliveryAndDiscountsAreExact() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Computers", "Acme", "Amazon", "30.00", specs("Ram Size", "8 GB")),
            product("b", "Electronics", "Acme", "Amazon", "25.00", specs()));
        List<BudgetRequest.CategoryRequirement> categories = List.of(
            required("Computers", 1), required("Electronics", 1));
        BudgetRequest base = request("200.00", categories, RecommendationMode.LOWEST_COST)
            .withCharges(java.util.Map.of("Amazon", new BigDecimal("5.00")),
                new BigDecimal("10"), new BigDecimal("50.00"));

        OptimizationResult withDiscount = service.optimize(catalogue, base);
        assertTrue(withDiscount.feasible());
        var breakdown = withDiscount.primary().breakdown();
        assertEquals(0, breakdown.itemsSubtotal().compareTo(new BigDecimal("55.00")));
        assertEquals(0, breakdown.deliveryCharges().compareTo(new BigDecimal("5.00")),
            "two products from one shop must not pay the delivery fee twice");
        assertEquals(0, breakdown.discount().compareTo(new BigDecimal("5.50")));
        assertEquals(0, breakdown.total().compareTo(new BigDecimal("54.50")));

        BudgetRequest aboveThreshold = base.withCharges(java.util.Map.of("Amazon", new BigDecimal("5.00")),
            new BigDecimal("10"), new BigDecimal("100.00"));
        OptimizationResult noDiscount = service.optimize(catalogue, aboveThreshold);
        assertEquals(0, noDiscount.primary().breakdown().discount().compareTo(BudgetMoney.ZERO));
        assertEquals(0, noDiscount.primary().breakdown().total().compareTo(new BigDecimal("60.00")));

        // and delivery must never appear twice in the totals the screen would show
        BudgetCalculationService calculator = new BudgetCalculationService();
        var costs = calculator.costs(base, withDiscount.primary().selected());
        assertEquals(0, costs.deliveryByStore().get("Amazon").compareTo(new BigDecimal("5.00")));
        assertEquals(1, costs.deliveryByStore().size());
    }

    // ------------------------------------------------------------------ 8. invalid input
    @Test
    @DisplayName("8. invalid budgets and constraints are rejected safely")
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BudgetRequest(new BigDecimal("-5.00"), "USD",
            List.of(required("Computers", 1)), tulita.budget.model.PreferenceWeights.DEFAULT,
            RecommendationMode.BEST_VALUE, java.util.Map.of(), BigDecimal.ZERO, BigDecimal.ZERO));

        BudgetRequest zeroBudget = BudgetRequest.of(BigDecimal.ZERO, "USD", List.of(required("Computers", 1)),
            tulita.budget.model.PreferenceWeights.DEFAULT, RecommendationMode.BEST_VALUE);
        assertFalse(zeroBudget.structuralErrors().isEmpty());
        assertThrows(OptimizationConstraintValidator.InvalidRequestException.class,
            () -> service.optimize(List.of(product("a", "Computers", "Acme", "Amazon", "10.00", specs())),
                zeroBudget));

        assertThrows(IllegalArgumentException.class, () -> new BudgetRequest.CategoryRequirement("Computers",
            true, 0, 2, null, BudgetRequest.Priority.MEDIUM, null, null));
        assertThrows(IllegalArgumentException.class, () -> new BudgetRequest.CategoryRequirement("Computers",
            false, 3, 2, null, BudgetRequest.Priority.MEDIUM, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new tulita.budget.model.PreferenceWeights(0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> BudgetMoney.of("fifty thousand"));

        // a bad minimum specification is caught by the validator, not silently ignored
        BudgetRequest badSpec = BudgetRequest.of(new BigDecimal("100.00"), "USD",
            List.of(new BudgetRequest.CategoryRequirement("Computers", true, 1, 1, null,
                BudgetRequest.Priority.MEDIUM, null, new BudgetRequest.MinimumSpec("Power (W)", 12))),
            tulita.budget.model.PreferenceWeights.DEFAULT, RecommendationMode.BEST_VALUE);
        assertThrows(OptimizationConstraintValidator.InvalidRequestException.class,
            () -> service.optimize(List.of(product("a", "Computers", "Acme", "Amazon", "10.00", specs())),
                badSpec));
    }

    // ------------------------------------------------------------------ 9. missing specifications
    @Test
    @DisplayName("9. missing specifications are neutral, disclosed, and never a perfect score")
    void missingSpecificationsAreTransparent() {
        String full = specs("Ram Size", "16 GB", "Hard Drive Capacity", "512 GB", "Processor Speed", "2.4 GHz",
            "Screen Size", "15.6 Inches", "Item Weight", "4 lb", "Warranty", "1 year");
        List<PhotoProduct> catalogue = List.of(
            product("silent", "Computers", "Acme", "Amazon", "100.00", "[]"),
            product("documented", "Computers", "Acme", "Walmart", "100.00", full));
        OptimizationResult result = service.optimize(catalogue,
            request("500.00", List.of(required("Computers", 1)), RecommendationMode.BEST_VALUE));

        assertTrue(result.feasible());
        ProductScore silent = result.primary().selected().stream()
            .map(SelectedProduct::score)
            .filter(s -> s.candidate().productId().equals("silent")).findFirst().orElse(null);
        var scored = service.scoring().scoreAll(
            service.scoring().candidates(catalogue, java.util.Set.of("Computers")),
            request("500.00", List.of(required("Computers", 1)), RecommendationMode.BEST_VALUE));
        ProductScore silentScore = scored.scores().stream()
            .filter(s -> s.candidate().productId().equals("silent")).findFirst().orElseThrow();
        ProductScore documented = scored.scores().stream()
            .filter(s -> s.candidate().productId().equals("documented")).findFirst().orElseThrow();

        assertFalse(silentScore.specAvailable());
        assertEquals(0.5, silentScore.component(ScoringCriterion.SPECIFICATIONS), 1e-9,
            "missing data must be neutral, never a perfect score");
        assertTrue(silentScore.notes().stream().anyMatch(n -> n.contains("neutral 0.5")));
        assertTrue(documented.specAvailable());
        assertTrue(documented.component(ScoringCriterion.SPECIFICATIONS) > 0.5);
        assertTrue(documented.total() > silentScore.total());
        assertTrue(scored.limitations().stream().anyMatch(l -> l.contains("no comparable specification")));
        assertNull(silent, "the undocumented product is not the best value choice");
    }

    // ------------------------------------------------------------------ 10. determinism
    @Test
    @DisplayName("10. identical input produces identical output")
    void resultsAreDeterministic() {
        List<PhotoProduct> catalogue = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            catalogue.add(productAt("p" + i, "Computers", "Brand" + (i % 3), i % 2 == 0 ? "Amazon" : "Walmart",
                String.valueOf(200 + i * 37), specs("Ram Size", (4 + i) + " GB"), i));
        }
        for (int i = 0; i < 8; i++) {
            catalogue.add(productAt("e" + i, "Electronics", "Brand" + (i % 2), i % 3 == 0 ? "Target" : "Amazon",
                String.valueOf(20 + i * 9), specs(), i));
        }
        BudgetRequest budgetRequest = request("1200.00",
            List.of(required("Computers", 1), required("Electronics", 2)), RecommendationMode.BALANCED);

        OptimizationResult first = service.optimize(catalogue, budgetRequest);
        OptimizationResult second = service.optimize(catalogue, budgetRequest);
        assertTrue(first.feasible() && second.feasible());
        assertEquals(first.primary().signature(), second.primary().signature());
        assertEquals(0, first.primary().breakdown().total().compareTo(second.primary().breakdown().total()));
        assertEquals(first.alternatives().size(), second.alternatives().size());
        for (int i = 0; i < first.alternatives().size(); i++) {
            assertEquals(first.alternatives().get(i).signature(), second.alternatives().get(i).signature());
        }
    }

    // ------------------------------------------------------------------ 11. duplicates
    @Test
    @DisplayName("11. the same combination is never presented as two alternatives")
    void alternativesAreDistinct() {
        List<PhotoProduct> catalogue = List.of(
            product("only", "Computers", "Acme", "Amazon", "300.00", specs("Ram Size", "8 GB")));
        OptimizationResult result = service.optimize(catalogue,
            request("900.00", List.of(required("Computers", 1)), RecommendationMode.BEST_VALUE));

        assertTrue(result.feasible());
        Set<String> seen = new LinkedHashSet<>();
        assertTrue(seen.add(result.primary().signature()));
        for (RecommendationAlternative alternative : result.alternatives()) {
            assertTrue(seen.add(alternative.signature()),
                "an identical basket must not appear twice: " + alternative.label());
            assertFalse(alternative.differences().isEmpty());
            assertFalse(alternative.advantages().isEmpty());
            assertFalse(alternative.disadvantages().isEmpty());
        }
        assertTrue(result.alternatives().size() <= 3);
    }

    // ------------------------------------------------------------------ 12. money precision
    @Test
    @DisplayName("12. decimal prices stay exact with BigDecimal")
    void moneyStaysExact() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Computers", "Acme", "Amazon", "0.10", specs("Ram Size", "8 GB")),
            product("b", "Electronics", "Acme", "Amazon", "0.20", specs()));
        OptimizationResult result = service.optimize(catalogue, request("10.00",
            List.of(required("Computers", 1), required("Electronics", 1)), RecommendationMode.LOWEST_COST));
        assertEquals(0, result.primary().breakdown().itemsSubtotal().compareTo(new BigDecimal("0.30")));

        List<PhotoProduct> thirds = List.of(
            product("c", "Electronics", "Acme", "Walmart", "33.33", specs()));
        OptimizationResult three = service.optimize(thirds,
            request("200.00", List.of(required("Electronics", 3)), RecommendationMode.LOWEST_COST));
        assertEquals(0, three.primary().breakdown().itemsSubtotal().compareTo(new BigDecimal("99.99")));

        BudgetRequest discount = request("200.00", List.of(required("Electronics", 3)),
            RecommendationMode.LOWEST_COST).withCharges(java.util.Map.of(), new BigDecimal("10.00"),
            BigDecimal.ZERO);
        OptimizationResult discounted = service.optimize(thirds, discount);
        assertEquals(0, discounted.primary().breakdown().discount().compareTo(new BigDecimal("10.00")),
            "9.999 rounds half-up to 10.00");
        assertEquals(0, discounted.primary().breakdown().total().compareTo(new BigDecimal("89.99")));

        assertEquals("USD 1,23,456.00", BudgetMoney.format(new BigDecimal("123456"), "USD"));
        assertEquals("USD 0.30", BudgetMoney.format(new BigDecimal("0.3"), "USD"));
    }

    // ------------------------------------------------------------------ ledger & self-check
    @Test
    @DisplayName("13. the optimiser re-checks its own basket and refuses to break a mandatory rule")
    void selfCheckCatchesInvalidBaskets() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Computers", "Acme", "Amazon", "100.00", specs("Ram Size", "8 GB")));
        BudgetRequest budgetRequest = request("500.00", List.of(required("Computers", 1)),
            RecommendationMode.LOWEST_COST);
        OptimizationResult result = service.optimize(catalogue, budgetRequest);
        OptimizationConstraintValidator validator = service.validator();
        assertEquals(List.of(), validator.violations(budgetRequest, result.primary().selected(),
            result.primary().breakdown().total(), Set.of("Computers")));

        SelectedProduct line = result.primary().selected().get(0);
        SelectedProduct tooMany = SelectedProduct.of(line.candidate(), 2, line.score(), 1.0, "test", "test");
        List<String> violations = validator.violations(budgetRequest, List.of(tooMany),
            new BigDecimal("200.00"), Set.of("Computers"));
        assertFalse(violations.isEmpty());
        assertTrue(violations.toString().contains("outside the allowed range"));

        // over-budget baskets are caught as well
        List<String> budgetViolation = validator.violations(budgetRequest, List.of(line),
            new BigDecimal("900.00"), Set.of("Computers"));
        assertTrue(budgetViolation.toString().contains("exceeds the budget"));
    }

    // ------------------------------------------------------------------ history & export
    @Test
    @DisplayName("14. recommendations can be saved as JSON and exported as CSV")
    void saveAndExport(@TempDir Path temp) throws Exception {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Computers", "Acme", "Amazon", "300.00", specs("Ram Size", "8 GB")));
        BudgetRequest budgetRequest = request("900.00", List.of(required("Computers", 1)),
            RecommendationMode.BEST_VALUE);
        OptimizationResult result = service.optimize(catalogue, budgetRequest);

        JsonBudgetHistoryDao dao = new JsonBudgetHistoryDao(temp.resolve("budget.json"));
        assertTrue(dao.list().isEmpty());
        dao.save(tulita.budget.model.SavedBudgetRun.of("Best Value", "USD",
            result.primary().breakdown().budget(), result.primary().breakdown().itemsSubtotal(),
            result.primary().breakdown().deliveryCharges(), result.primary().breakdown().discount(),
            result.primary().breakdown().total(), result.primary().breakdown().remaining(),
            "price 30 %, specifications 30 %", List.of(new tulita.budget.model.SavedBudgetRun.Line(
            "Computers", "a", "Product a", "Amazon", 1, new BigDecimal("300.00"),
            new BigDecimal("300.00"), 0.5, "")), List.of("historical prices")));
        List<tulita.budget.model.SavedBudgetRun> saved = dao.list();
        assertEquals(1, saved.size());
        assertEquals(0, saved.get(0).total().compareTo(new BigDecimal("300.00")));
        assertEquals("Computers", saved.get(0).lines().get(0).category());

        Path csv = new tulita.budget.service.BudgetExportService().export(result, budgetRequest,
            temp.resolve("plan.csv"));
        String text = Files.readString(csv);
        assertTrue(text.contains("TULITA Budget Optimizer report"));
        assertTrue(text.contains("Estimated total"));
        assertTrue(text.contains("Product a"));
        assertTrue(text.contains("LIMITATIONS AND DATA NOTES"));
        assertTrue(text.contains("not live retailer prices"));
    }

    // ------------------------------------------------------------------ real catalogue
    @Test
    @DisplayName("15. the planner works against the real curated catalogue")
    void worksOnTheRealCatalogue() throws Exception {
        Path root = tulita.photocatalog.dao.JsonPhotoCatalogDao.defaultRoot();
        Assumptions.assumeTrue(Files.isDirectory(root), "curated catalogue not present");
        List<PhotoProduct> products = new tulita.photocatalog.dao.JsonPhotoCatalogDao(root).load();
        Assumptions.assumeFalse(products.isEmpty(), "curated catalogue is empty");

        String currency = service.currency(products);
        assertEquals("USD", currency);
        var categories = service.categories(products);
        assertTrue(categories.size() >= 10);
        assertTrue(categories.containsKey("Computers"));

        List<BudgetRequest.CategoryRequirement> requirements = List.of(
            new BudgetRequest.CategoryRequirement("Computers", true, 1, 1, null,
                BudgetRequest.Priority.HIGH, null, null),
            new BudgetRequest.CategoryRequirement("Kitchen & Appliances", true, 1, 1, null,
                BudgetRequest.Priority.MEDIUM, null, null),
            new BudgetRequest.CategoryRequirement("Toys & Games", false, 0, 1, null,
                BudgetRequest.Priority.LOW, null, null));
        BudgetRequest budgetRequest = BudgetRequest.of(new BigDecimal("2500.00"), currency, requirements,
            tulita.budget.model.PreferenceWeights.DEFAULT, RecommendationMode.BEST_VALUE);

        OptimizationResult result = service.optimize(products, budgetRequest);
        assertTrue(result.feasible(), () -> "expected a feasible plan: " + result.infeasibleReasons());
        assertTrue(result.primary().breakdown().withinBudget());
        assertTrue(result.primary().satisfiedCategories().size() >= 2);
        assertTrue(result.primary().selected().stream()
            .allMatch(line -> !line.explanation().isBlank() && !line.tradeOff().isBlank()));
        assertTrue(result.limitations().stream().anyMatch(l -> l.contains("Ratings")));
        assertFalse(result.algorithmNote().isBlank());

        OptimizationResult cheapest = service.optimize(products,
            budgetRequest.withMode(RecommendationMode.LOWEST_COST));
        assertTrue(cheapest.feasible());
        assertTrue(cheapest.primary().breakdown().total()
            .compareTo(result.primary().breakdown().total()) <= 0);
    }

    // ------------------------------------------------------------------ same variant, two shops
    @Test
    @DisplayName("16. one variant listed by two shops keeps one specification profile")
    void listingsOfOneVariantScoreTheSameOnSpecifications() {
        String detailed = specs("Ram Size", "8 GB", "Hard Drive Capacity", "512 GB", "Processor Speed", "2.0 GHz",
            "Screen Size", "15.6 Inches", "Item Weight", "4 lb", "Warranty", "1 year");
        String sparse = specs("Ram Size", "8 GB");
        PhotoProduct.Offer amazonOffer = new PhotoProduct.Offer("Amazon", new BigDecimal("700.00"), "USD", "T",
            "u", "", "f.csv", 1, 1, "0".repeat(64), detailed, "d");
        PhotoProduct.Offer walmartOffer = new PhotoProduct.Offer("Walmart", new BigDecimal("600.00"), "USD", "T",
            "u", "", "f.csv", 1, 2, "0".repeat(64), sparse, "d");
        PhotoProduct variant = new PhotoProduct("same", "Product same", "Acme", "M1", "single unit", "Computers",
            "test fixture", List.of(amazonOffer, walmartOffer), List.of());

        var candidates = service.scoring().candidates(List.of(variant), Set.of("Computers"));
        assertEquals(2, candidates.size());
        assertEquals(candidates.get(0).metrics(), candidates.get(1).metrics(),
            "the same variant must not gain specification credit from whichever shop documents more");

        OptimizationResult result = service.optimize(List.of(variant),
            request("1000.00", List.of(required("Computers", 1)), RecommendationMode.LOWEST_COST));
        assertTrue(result.feasible());
        assertEquals("Walmart", result.primary().selected().get(0).candidate().store());
    }

    @Test
    @DisplayName("17. a warranty written in days is converted, not read as months")
    void warrantyDaysAreConverted() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Kitchen & Appliances", "Acme", "Amazon", "50.00",
                specs("Warranty Description", "180-day limited warranty.")),
            product("b", "Kitchen & Appliances", "Acme", "Walmart", "50.00", specs()));
        var scored = service.scoring().scoreAll(
            service.scoring().candidates(catalogue, Set.of("Kitchen & Appliances")),
            request("500.00", List.of(required("Kitchen & Appliances", 1)), RecommendationMode.BEST_VALUE));
        ProductScore a = scored.scores().stream().filter(s -> s.candidate().productId().equals("a"))
            .findFirst().orElseThrow();
        double months = a.candidate().metrics().get("Warranty (months)");
        assertEquals(6.0, months, 1e-6);
    }

    @Test
    @DisplayName("18. per-line charges add up to exactly the basket total")
    void lineChargesReconcileWithTotal() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Kitchen & Appliances", "Acme", "Amazon", "40.00", specs()),
            product("b", "Kitchen & Appliances", "Acme", "Amazon", "55.50", specs()),
            product("c", "Kitchen & Appliances", "Acme", "Walmart", "29.99", specs()));
        BudgetRequest request = request("500.00",
            List.of(required("Kitchen & Appliances", 3)), RecommendationMode.LOWEST_COST);
        OptimizationResult result = service.optimize(catalogue, request);
        assertTrue(result.feasible());
        Map<String, LineCharges> charges = service.lineCharges(request, result.primary());
        assertEquals(result.primary().selected().size(), charges.size());
        BigDecimal sum = charges.values().stream().map(LineCharges::finalCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(result.primary().breakdown().total()),
            "allocated final costs must equal the basket total: " + sum + " vs "
                + result.primary().breakdown().total());
    }

    @Test
    @DisplayName("19. a delivery fee and a discount are apportioned, never invented")
    void lineChargesCarryFeesAndDiscounts() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Kitchen & Appliances", "Acme", "Amazon", "100.00", specs()),
            product("b", "Kitchen & Appliances", "Acme", "Walmart", "50.00", specs()));
        BudgetRequest base = request("500.00",
            List.of(required("Kitchen & Appliances", 2)), RecommendationMode.BEST_VALUE);
        BudgetRequest charged = base.withCharges(Map.of("Amazon", new BigDecimal("10.00"),
            "Walmart", new BigDecimal("5.00")), new BigDecimal("10.00"), new BigDecimal("50.00"));
        OptimizationResult result = service.optimize(catalogue, charged);
        assertTrue(result.feasible());
        Map<String, LineCharges> charges = service.lineCharges(charged, result.primary());
        BigDecimal sum = charges.values().stream().map(LineCharges::finalCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(result.primary().breakdown().total()));
        assertTrue(charges.values().stream().anyMatch(c -> c.delivery().signum() > 0),
            "at least one line must carry a delivery share");
        assertTrue(charges.values().stream().anyMatch(c -> c.discount().signum() > 0),
            "the eligible discount must reach the lines");
        for (LineCharges line : charges.values()) {
            assertTrue(line.finalCost().signum() > 0);
            assertTrue(line.hasCharges());
        }
    }

    @Test
    @DisplayName("20. the same request gives identical line charges (deterministic apportioning)")
    void lineChargesAreDeterministic() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Kitchen & Appliances", "Acme", "Amazon", "33.33", specs()),
            product("b", "Kitchen & Appliances", "Acme", "Amazon", "33.33", specs()),
            product("c", "Kitchen & Appliances", "Acme", "Amazon", "33.34", specs()));
        BudgetRequest request = request("500.00",
            List.of(required("Kitchen & Appliances", 3)), RecommendationMode.BEST_VALUE)
            .withCharges(Map.of("Amazon", new BigDecimal("7.77")), new BigDecimal("5.00"),
                new BigDecimal("10.00"));
        OptimizationResult result = service.optimize(catalogue, request);
        Map<String, LineCharges> first = service.lineCharges(request, result.primary());
        Map<String, LineCharges> second = service.lineCharges(request, result.primary());
        assertEquals(first, second);
        BigDecimal sum = first.values().stream().map(LineCharges::finalCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(result.primary().breakdown().total()));
    }

    @Test
    @DisplayName("21. every what-if option is a valid, independent request")
    void whatIfOptionsAreValidRequests() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Kitchen & Appliances", "Acme", "Amazon", "40.00", specs()),
            product("b", "Kitchen & Appliances", "Acme", "Walmart", "55.00", specs()));
        BudgetRequest request = request("500.00",
            List.of(required("Kitchen & Appliances", 1)), RecommendationMode.BEST_VALUE);
        List<WhatIfOption> options = service.whatIfOptions(request);
        assertFalse(options.isEmpty(), "the panel must offer something to explore");
        Set<String> labels = new HashSet<>();
        for (WhatIfOption option : options) {
            assertTrue(labels.add(option.label()), "duplicate what-if option: " + option.label());
            assertNotNull(option.request());
            // each option must be optimisable on its own, without touching the original request
            OptimizationResult result = service.optimize(catalogue, option.request());
            assertNotNull(result);
            assertTrue(result.primary() == null || result.primary().breakdown().total()
                .compareTo(option.request().budget()) <= 0, "a what-if must never exceed its own budget");
        }
        assertEquals("500.00", request.budget().setScale(2, RoundingMode.HALF_UP).toPlainString(),
            "the original request must not be mutated by building options");
    }

    @Test
    @DisplayName("22. a what-if that changes nothing reports no change, one that does reports a delta")
    void deltaDescribesWhatMoved() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Kitchen & Appliances", "Acme", "Amazon", "40.00", specs()),
            product("b", "Kitchen & Appliances", "Acme", "Walmart", "55.00", specs()));
        BudgetRequest request = request("500.00",
            List.of(required("Kitchen & Appliances", 1)), RecommendationMode.BEST_VALUE);
        OptimizationResult first = service.optimize(catalogue, request);
        OptimizationResult again = service.optimize(catalogue, request);
        assertTrue(service.delta(first.primary(), again.primary()).noChange(),
            "optimising the same request twice must report no change");

        OptimizationResult cheaper = service.optimize(catalogue, request.withBudget(new BigDecimal("60.00")));
        RecommendationDelta delta = service.delta(first.primary(), cheaper.primary());
        assertNotNull(delta);
        assertFalse(delta.noChange());
        assertTrue(delta.totalChange().signum() <= 0);
        assertTrue(delta.totalChangeText().contains("less") || delta.totalChange().signum() == 0);
        assertEquals(null, service.delta(null, cheaper.primary()));
    }

    @Test
    @DisplayName("23. an INR-priced catalogue is optimised and printed in INR, with Indian grouping")
    void inrCatalogueWorksWithoutConversion() {
        assertEquals("INR 1,23,456.78", BudgetMoney.format(new BigDecimal("123456.78"), "INR"));
        List<PhotoProduct> catalogue = List.of(
            productIn("INR", "laptop-a", "Computers", "Acme", "Flipkart", "48000.00", specs("Storage", "512 GB")),
            productIn("INR", "laptop-b", "Computers", "Acme", "Amazon", "62500.00", specs("Storage", "1 TB")),
            productIn("INR", "kettle", "Kitchen & Appliances", "Acme", "Flipkart", "1800.00", specs()));
        BudgetRequest request = new BudgetRequest(new BigDecimal("50000"), "INR",
            List.of(required("Computers", 1), required("Kitchen & Appliances", 1)),
            PreferenceWeights.DEFAULT, RecommendationMode.LOWEST_COST, Map.of(), BigDecimal.ZERO,
            BigDecimal.ZERO);
        OptimizationResult result = service.optimize(catalogue, request);
        assertTrue(result.feasible(), () -> "expected a feasible INR plan: " + result.infeasibleReasons());
        assertEquals("INR", result.primary().breakdown().currency());
        assertTrue(result.primary().breakdown().total().compareTo(new BigDecimal("50000")) <= 0);
        assertTrue(BudgetMoney.format(result.primary().breakdown().total(), "INR").startsWith("INR "),
            "amounts must carry the catalogue currency, never a guessed symbol");
        assertFalse(service.lineCharges(request, result.primary()).isEmpty());
    }

    @Test
    @DisplayName("24. a budget in a different currency is refused, never silently converted")
    void currencyMismatchIsRefused() {
        List<PhotoProduct> catalogue = List.of(
            productIn("INR", "laptop-a", "Computers", "Acme", "Flipkart", "48000.00", specs()));
        BudgetRequest rupees = request("50000.00", List.of(required("Computers", 1)),
            RecommendationMode.BEST_VALUE).withBudget(new BigDecimal("50000"));
        // the fixture request is USD, the catalogue is INR: the planner must say so and stop
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.optimize(catalogue, rupees));
        assertTrue(error.getMessage().contains("INR") && error.getMessage().contains("USD"),
            "the message must name both currencies: " + error.getMessage());
        assertTrue(error.getMessage().toLowerCase().contains("conversion"),
            "the message must state that no conversion is performed");
    }

    @Test
    @DisplayName("25. the mode really optimises its stated objective: Best Value cannot lose on weighted score")
    void bestValueWinsOnTheWeightedObjective() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Kitchen & Appliances", "Acme", "Amazon", "40.00",
                specs("Capacity", "8 oz", "Warranty Description", "2 years")),
            product("b", "Kitchen & Appliances", "Acme", "Walmart", "95.00",
                specs("Capacity", "24 oz", "Warranty Description", "5 years")),
            product("c", "Kitchen & Appliances", "Acme", "Target", "60.00",
                specs("Capacity", "16 oz", "Warranty Description", "3 years")),
            product("d", "Toys & Games", "Acme", "Amazon", "12.00", specs()));
        BudgetRequest.CategoryRequirement kitchen = new BudgetRequest.CategoryRequirement(
            "Kitchen & Appliances", true, 1, 2, null, BudgetRequest.Priority.HIGH, null, null);
        BudgetRequest.CategoryRequirement toys = new BudgetRequest.CategoryRequirement(
            "Toys & Games", true, 1, 2, null, BudgetRequest.Priority.LOW, null, null);
        BudgetRequest probe = new BudgetRequest(new BigDecimal("1000"), "USD",
            List.of(kitchen, toys), PreferenceWeights.DEFAULT, RecommendationMode.BEST_VALUE,
            Map.of(), BigDecimal.ZERO, BigDecimal.ZERO);

        BudgetRecommendation bestValue = service.optimize(catalogue, probe).primary();
        BudgetRecommendation lowestCost = service.optimize(catalogue,
            probe.withMode(RecommendationMode.LOWEST_COST)).primary();
        double weightedBest = BudgetOptimizationEngine.weightedScore(bestValue, probe);
        double weightedCheap = BudgetOptimizationEngine.weightedScore(lowestCost, probe);
        assertTrue(weightedBest >= weightedCheap - 1e-9,
            "Best Value must not lose on the objective it optimises: " + weightedBest + " vs " + weightedCheap);
        assertTrue(lowestCost.breakdown().total().compareTo(bestValue.breakdown().total()) <= 0,
            "Lowest Cost must not cost more than Best Value");
        // the plain average ignores priority and quantity, so it is allowed to differ from the weighted one
        assertTrue(weightedBest >= 0 && weightedBest <= 1);
    }

    @Test
    @DisplayName("26. an INR budget is converted with the user's own rate, never an invented one")
    void userDeclaredRateConvertsTheBudget() {
        CurrencyConverter inr = CurrencyConverter.userDeclared("USD", "INR", "83.50");
        assertTrue(inr.converts());
        // Rs 2,00,000 at 83.50 Rs/USD
        assertEquals(0, inr.toCatalogue(new BigDecimal("200000")).compareTo(new BigDecimal("2395.21")),
            "the optimiser must receive the catalogue-currency amount: " + inr.toCatalogue(new BigDecimal("200000")));
        assertEquals("\u20b9".isEmpty() ? "" : "INR 57,485.58",
            BudgetMoney.format(inr.toDisplay(new BigDecimal("688.45")), "INR"));
        assertTrue(inr.format(new BigDecimal("688.45")).startsWith("INR "));
        assertTrue(inr.describe().contains("83.50"), "the assumption must be stated: " + inr.describe());
        assertTrue(inr.describe().toLowerCase().contains("does not supply exchange rates"));
    }

    @Test
    @DisplayName("27. the rate is validated and a bad rate never silently falls back")
    void rateValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> CurrencyConverter.userDeclared("USD", "INR", ""), "a blank rate must be rejected");
        assertThrows(IllegalArgumentException.class,
            () -> CurrencyConverter.userDeclared("USD", "INR", "abc"));
        assertThrows(IllegalArgumentException.class,
            () -> CurrencyConverter.userDeclared("USD", "INR", "0"));
        assertThrows(IllegalArgumentException.class,
            () -> CurrencyConverter.userDeclared("USD", "INR", "-83.5"));
        // same currency: no conversion at all, no rate needed
        assertFalse(CurrencyConverter.userDeclared("USD", "USD", "").converts());
        assertFalse(CurrencyConverter.catalogueOnly("USD").converts());
    }

    @Test
    @DisplayName("28. conversion is monotonic: a basket inside the budget never looks over it in INR")
    void conversionCannotBreakTheBudgetGuarantee() {
        List<PhotoProduct> catalogue = List.of(
            product("a", "Computers", "Acme", "Amazon", "629.99", specs()),
            product("b", "Computers", "Acme", "Walmart", "899.00", specs()));
        BudgetRequest usd = new BudgetRequest(new BigDecimal("2395.21"), "USD",
            List.of(required("Computers", 1)), PreferenceWeights.DEFAULT,
            RecommendationMode.LOWEST_COST, Map.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        OptimizationResult result = service.optimize(catalogue, usd);
        assertTrue(result.feasible());
        CurrencyConverter inr = CurrencyConverter.userDeclared("USD", "INR", "83.50");
        BigDecimal budgetInr = inr.toDisplay(usd.budget());
        BigDecimal totalInr = inr.toDisplay(result.primary().breakdown().total());
        assertTrue(totalInr.compareTo(budgetInr) <= 0,
            "converted total " + totalInr + " must not exceed converted budget " + budgetInr);
        // and the same invariant holds at an awkward rate
        CurrencyConverter odd = CurrencyConverter.userDeclared("USD", "INR", "83.333333");
        assertTrue(odd.toDisplay(result.primary().breakdown().total())
            .compareTo(odd.toDisplay(usd.budget())) <= 0);
    }
}
