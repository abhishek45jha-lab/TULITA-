package tulita.budget.service;

import tulita.dao.DataAccessException;
import tulita.database.AppConfig;
import tulita.database.ConnectionFactory;
import tulita.dataset.DatasetConfig;
import tulita.dataset.dao.*;
import tulita.dataset.model.DatasetSnapshot;

import java.io.IOException;
import java.util.Locale;

/**
 * Loads the same local catalogue used by the dataset screens into the budget planner.
 * It never scrapes, never falls back to demo products and never invents prices: if the configured
 * source cannot be read, the exception reaches the UI so the user can fix the path.
 *
 * <p>{@code TULITA_BUDGET_SOURCE} may be {@code multistore}/{@code multi} or
 * {@code flipkart}/{@code single}. When it is unset, the bundled three-store ZIP is preferred.</p>
 */
public final class BudgetDataLoader {
    public record Loaded(BudgetCatalogService catalog, DatasetSnapshot snapshot, boolean multi) {
        public String sourceLabel() {
            return snapshot.sourceTitle() + " · " + snapshot.storage()
                    + (multi ? " (Amazon + Flipkart + Myntra)" : "");
        }
    }

    private BudgetDataLoader() { }

    public static Loaded load(boolean multi) throws IOException, DataAccessException {
        DatasetConfig config = DatasetConfig.load();
        DatasetDao dao = multi
                ? (config.storage().equals("mysql")
                    ? new MultiJdbcDao(new ConnectionFactory(AppConfig.load()))
                    : new MultiCsvDao(MultiCsvDao.defaultPath()))
                : (config.storage().equals("mysql")
                    ? new JdbcDatasetDao(new ConnectionFactory(AppConfig.load()))
                    : new CsvDatasetDao(config.csvPath()));
        DatasetSnapshot snapshot = dao.load();
        return new Loaded(new BudgetCatalogService(snapshot.products()), snapshot, multi);
    }

    /** True when the multistore catalogue should be used by default. */
    public static boolean defaultMulti() {
        String source = System.getenv("TULITA_BUDGET_SOURCE");
        if (source != null) {
            String value = source.toLowerCase(Locale.ROOT).strip();
            if (value.equals("multi") || value.equals("multistore") || value.equals("three")) return true;
            if (value.equals("single") || value.equals("flipkart") || value.equals("demo")) return false;
        }
        return java.nio.file.Files.exists(MultiCsvDao.defaultPath());
    }

    public static String databaseHint() {
        return "Check TULITA_DATA_SOURCE (csv/mysql), TULITA_DATASET_PATH, "
                + "TULITA_BUDGET_SOURCE (multistore/flipkart) and TULITA_MULTI_DATASET_PATH. "
                + "No demo or fabricated product is used as a fallback.";
    }
}
