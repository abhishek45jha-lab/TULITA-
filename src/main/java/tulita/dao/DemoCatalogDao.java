package tulita.dao;
import tulita.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

public final class DemoCatalogDao implements CatalogDao {
    @Override public CatalogSnapshot load() throws DataAccessException {
        try {
            List<ProductDetails> products = new ArrayList<>();
            for (String[] p : rows("products.psv")) products.add(new ProductDetails(Long.parseLong(p[0]),p[1],p[2],p[3],p[4],p[5],p[6]));
            List<RawListing> listings = new ArrayList<>();
            for (String[] p : rows("listings.psv")) listings.add(new RawListing(Long.parseLong(p[0]),Long.parseLong(p[1]),p[2],p[3],p[4],p[5],money(p[6]),money(p[7]),money(p[8]),money(p[9]),p[10],Boolean.parseBoolean(p[11]),p[12]));
            return new CatalogSnapshot(products, listings);
        } catch (IOException | RuntimeException e) { throw new DataAccessException("Bundled sample data could not be read. Rebuild the project.", e); }
    }
    private BigDecimal money(String s) { return s.isBlank() ? null : new BigDecimal(s); }
    private List<String[]> rows(String name) throws IOException {
        InputStream stream = getClass().getResourceAsStream("/data/" + name);
        if (stream == null) throw new IOException("Missing " + name);
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().filter(s -> !s.isBlank() && !s.startsWith("#")).map(s -> s.split("\\|", -1)).toList();
        }
    }
}
