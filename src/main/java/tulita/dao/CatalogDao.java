package tulita.dao;
import java.util.List;
import tulita.model.*;
/** A single snapshot avoids a database round-trip for each search keystroke. */
public interface CatalogDao {
    CatalogSnapshot load() throws DataAccessException;
    record CatalogSnapshot(List<ProductDetails> products, List<RawListing> listings) {
        public CatalogSnapshot { products = List.copyOf(products); listings = List.copyOf(listings); }
    }
}
