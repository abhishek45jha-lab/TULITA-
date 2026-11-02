package tulita.budget;

import org.junit.jupiter.api.Test;
import tulita.budget.model.BudgetCategory;
import tulita.budget.service.BudgetClassifier;
import tulita.budget.service.SourceText;
import tulita.budget.service.SpecExtractor;
import tulita.budget.service.SpecProfile;
import tulita.dataset.model.DatasetProduct;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Strict category evidence, Hindi normalisation and no invented specifications. */
public class BudgetClassifierTest {
    private static DatasetProduct product(String title, String category, String price) {
        return new DatasetProduct("id", "id", title, category, "", "", new BigDecimal(price), null, null,
                "2023-01-01 10:00:00 +0000", "u", "", "");
    }

    @Test
    void recognisesTheFourPlanningCategories() {
        assertEquals(Optional.of(BudgetCategory.LAPTOPS),
                BudgetClassifier.classify(product("HP Laptop Core i5 8GB RAM 512GB SSD", "Computers", "45000")));
        assertEquals(Optional.of(BudgetCategory.KEYBOARDS),
                BudgetClassifier.classify(product("Logitech K120 Wired Keyboard", "Computers", "699")));
        assertEquals(Optional.of(BudgetCategory.MICE),
                BudgetClassifier.classify(product("HP Wireless Mouse 1600 DPI", "Computers", "800")));
        assertEquals(Optional.of(BudgetCategory.HEADPHONES),
                BudgetClassifier.classify(product("Sony Wireless Headphone 30 hours", "Mobiles & Accessories", "15000")));
    }

    @Test
    void recognisesHindiProductWords() {
        assertTrue(SourceText.normalize("कीबोर्ड").contains("keyboard"));
        assertTrue(SourceText.normalize("माउस").contains("mouse"));
        assertTrue(SourceText.normalize("लैपटॉप").contains("laptop"));
        assertEquals(Optional.of(BudgetCategory.KEYBOARDS),
                BudgetClassifier.classify(product("Logitech वायरलेस कीबोर्ड", "कंप्यूटर एक्सेसरी", "1499")));
        assertEquals(Optional.of(BudgetCategory.MICE),
                BudgetClassifier.classify(product("HP वायरलेस माउस 1600 DPI", "कंप्यूटर और सहायक उपकरण", "799")));
    }

    @Test
    void rejectsAccessoriesAndBundles() {
        assertEquals(Optional.empty(), BudgetClassifier.classify(product("FRENEMY Mousepad Large", "Computers", "399")));
        assertEquals(Optional.empty(), BudgetClassifier.classify(product("Mickey Mouse Pencil Box", "Stationery", "399")));
        assertEquals(Optional.empty(), BudgetClassifier.classify(product("Dell Laptop Bag", "Computers", "1500")));
        assertEquals(Optional.empty(), BudgetClassifier.classify(product("Casio Musical Keyboard Piano 61 key", "Musical Instruments", "9000")));
        assertEquals(Optional.empty(), BudgetClassifier.classify(product("Headphone Cushion Replacement", "Mobiles & Accessories", "499")));
        assertEquals(Optional.empty(), BudgetClassifier.classify(product("HP Keyboard and Mouse Combo", "Computers", "1999")));
    }

    @Test
    void extractsRecordedSpecsAndDoesNotGuessMissingOnes() {
        SpecProfile full = SpecExtractor.extract(BudgetCategory.LAPTOPS,
                "HP Laptop 12th Gen Intel Core i5 16GB RAM 512GB SSD backlit", "", "");
        assertEquals("Core i5", full.processor());
        assertEquals(16, full.ramGb());
        assertEquals(512, full.storageGb());
        assertEquals(12, full.generation());
        assertTrue(full.backlit());

        // P17 is a model number, not a 17th generation.
        SpecProfile modelNumber = SpecExtractor.extract(BudgetCategory.LAPTOPS,
                "Lenovo ThinkPad P17 Gen 2 i7 64GB RAM 2TB SSD", "", "");
        assertNull(modelNumber.generation());
        assertEquals("Core i7", modelNumber.processor());
        assertEquals(64, modelNumber.ramGb());
        assertEquals(2048, modelNumber.storageGb());

        SpecProfile sparse = SpecExtractor.extract(BudgetCategory.HEADPHONES, "Wired Headphone", "", "");
        assertNull(sparse.batteryHours());
        assertFalse(sparse.noiseCancelling());
        assertNull(sparse.dpi());
    }
}
