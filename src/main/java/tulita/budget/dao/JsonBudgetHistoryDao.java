package tulita.budget.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import tulita.budget.model.SavedBudgetRun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves recommendations to a single JSON file using the Jackson dependency the project already has.
 *
 * <p>Location: {@code $TULITA_BUDGET_STORE} when set, otherwise {@code ~/.tulita/budget-recommendations.json}.
 * The file sits beside the downloaded vision model, never inside the read-only catalogue folder, and the
 * existing catalogue files are never written to.</p>
 */
public final class JsonBudgetHistoryDao implements BudgetHistoryDao {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonBudgetHistoryDao(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public static JsonBudgetHistoryDao defaultStore() {
        String override = System.getenv("TULITA_BUDGET_STORE");
        if (override != null && !override.isBlank()) {
            return new JsonBudgetHistoryDao(Path.of(override));
        }
        return new JsonBudgetHistoryDao(Path.of(System.getProperty("user.home"), ".tulita",
            "budget-recommendations.json"));
    }

    @Override
    public List<SavedBudgetRun> list() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        if (Files.size(file) == 0) {
            return List.of();
        }
        SavedBudgetRun[] runs = mapper.readValue(file.toFile(), SavedBudgetRun[].class);
        return List.of(runs);
    }

    @Override
    public void save(SavedBudgetRun run) throws IOException {
        List<SavedBudgetRun> all = new ArrayList<>(list());
        all.add(run);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temp.toFile(), all);
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Path location() {
        return file;
    }
}
