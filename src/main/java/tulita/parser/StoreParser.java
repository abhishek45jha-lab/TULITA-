package tulita.parser;
import tulita.model.*;
/** No network calls: initial adapters only validate bundled/SQL sample listing records. */
public interface StoreParser {
    String storeName();
    default Listing parse(RawListing raw) {
        if (!storeName().equals(raw.store())) throw new IllegalArgumentException("Wrong store adapter: " + raw.store());
        return new Listing(raw);
    }
}
