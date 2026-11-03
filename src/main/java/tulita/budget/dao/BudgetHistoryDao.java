package tulita.budget.dao;

import tulita.budget.model.SavedBudgetRun;

import java.nio.file.Path;
import java.util.List;

/**
 * Storage for saved recommendations.
 *
 * <p>The catalogue itself lives in JSON files or MySQL, but a saved plan is per-user state rather than
 * shared catalogue data, so it is kept out of the product tables. This interface leaves room for a
 * database-backed implementation without touching the rest of the planner.</p>
 */
public interface BudgetHistoryDao {

    List<SavedBudgetRun> list() throws Exception;

    void save(SavedBudgetRun run) throws Exception;

    Path location();
}
