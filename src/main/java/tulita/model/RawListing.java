package tulita.model;
import java.math.BigDecimal;
/** Store-shaped sample input; the parsers produce validated Listing objects. */
public record RawListing(long id, long productId, String store, String title, String model,
        String variant, BigDecimal price, BigDecimal mrp, BigDecimal rating,
        BigDecimal delivery, String estimate, boolean available, String url) { }
