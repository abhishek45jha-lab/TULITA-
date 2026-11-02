package tulita.budget;

import org.junit.jupiter.api.Test;
import tulita.budget.model.*;
import tulita.budget.service.BudgetCatalogService;
import tulita.budget.service.BudgetCsvExporter;
import tulita.budget.service.BudgetDataLoader;
import tulita.budget.service.BudgetOptimizer;
import tulita.dataset.model.DatasetProduct;
import tulita.dataset.model.DatasetSnapshot;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** CSV export keeps the calculated numbers, quotes commas and writes the source note. */
public class BudgetCsvExporterTest {
    @Test
    void exportsSummaryProductsAndEscapedTitle() throws Exception {
        List<DatasetProduct> products = List.of(
                new DatasetProduct("lap-1", "p1", "Dell Laptop, Core i3 8GB RAM 256GB SSD", "Computers", "", "Dell",
                        new BigDecimal("25000"), null, new BigDecimal("4.2"), "2023-01-01 10:00:00 +0000",
                        "https://example.test/lap-1", "", ""),
                new DatasetProduct("kb-1", "p2", "Logitech Wired Keyboard", "Computers", "", "Logitech",
                        new BigDecimal("700"), null, new BigDecimal("4.1"), "2023-01-01 10:00:00 +0000",
                        "https://example.test/kb-1", "", "")
        );
        BudgetCatalogService catalog = new BudgetCatalogService(products);
        BudgetRequest request = new BudgetRequest(new BigDecimal("30000"),
                List.of(new CategoryRequirement(BudgetCategory.LAPTOPS, true, 1),
                        new CategoryRequirement(BudgetCategory.KEYBOARDS, true, 1)),
                PlanMode.BEST_VALUE, Set.of());
        BudgetPlan plan = new BudgetOptimizer().optimize(request, catalog);
        assertTrue(plan.feasible());

        DatasetSnapshot snapshot = new DatasetSnapshot(products, "Test catalogue", "https://example.test",
                "CC BY-SA 4.0", "sha256", "2023-01-01", "2023-01-02", "CSV");
        BudgetDataLoader.Loaded loaded = new BudgetDataLoader.Loaded(catalog, snapshot, true);
        Path file = Files.createTempFile("tulita-budget-", ".csv");
        try {
            BudgetCsvExporter.export(file, plan, loaded);
            String csv = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(csv.startsWith("\uFEFF"));
            assertTrue(csv.contains("section,category,product"));
            assertTrue(csv.contains("Summary"));
            assertTrue(csv.contains("Total cost (INR)"));
            assertTrue(csv.contains("25700.00"));
            assertTrue(csv.contains("Dell Laptop, Core i3 8GB RAM 256GB SSD"), "The full title must be present");
            assertTrue(csv.contains("\"Dell Laptop, Core i3 8GB RAM 256GB SSD\""), "Commas must be quoted");
            assertTrue(csv.contains("Historical observed prices only"));
            assertTrue(csv.contains("Test catalogue"));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
