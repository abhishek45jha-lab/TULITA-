package tulita.budget;

import org.junit.jupiter.api.Test;
import tulita.budget.model.BudgetCategory;
import tulita.budget.model.BudgetProduct;
import tulita.budget.service.BudgetDataLoader;
import tulita.dataset.dao.MultiCsvDao;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/** Smoke test against the bundled three-store ZIP, when it is present in the checkout. */
public class BudgetDataLoaderTest {
    @Test
    void loadsRealBundledCatalogueWithoutInventingRows() throws Exception {
        if (!Files.exists(MultiCsvDao.defaultPath())) return;
        BudgetDataLoader.Loaded loaded = BudgetDataLoader.load(true);
        assertTrue(loaded.snapshot().products().size() > 50_000);
        assertTrue(loaded.catalog().totalProducts() > 500);
        for (BudgetCategory category : BudgetCategory.values()) {
            assertTrue(loaded.catalog().count(category) > 0, "No real rows classified as " + category);
            for (var scored : loaded.catalog().products(category)) {
                BudgetProduct product = scored.product();
                assertTrue(product.price().signum() > 0);
                assertEquals(category, product.category());
                assertFalse(product.title().isBlank());
                assertFalse(product.recordId().isBlank());
            }
        }
        assertNotNull(loaded.snapshot().sha256());
        assertFalse(loaded.snapshot().sha256().isBlank());
    }
}
