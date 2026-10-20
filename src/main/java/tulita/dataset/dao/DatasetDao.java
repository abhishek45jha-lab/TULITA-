package tulita.dataset.dao;
import tulita.dao.DataAccessException;
import tulita.dataset.model.DatasetSnapshot;
public interface DatasetDao { DatasetSnapshot load() throws DataAccessException; }
