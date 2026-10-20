package tulita.dao;
import tulita.database.ConnectionFactory;
import tulita.model.*;
import java.sql.*;
import java.util.*;

public final class JdbcCatalogDao implements CatalogDao {
    private final ConnectionFactory factory;
    public JdbcCatalogDao(ConnectionFactory factory) { this.factory = factory; }
    @Override public CatalogSnapshot load() throws DataAccessException {
        try (Connection c = factory.open()) {
            c.setReadOnly(true);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setAutoCommit(false);
            try {
                List<ProductDetails> products = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT id,name,brand,model,category,variant,art FROM products ORDER BY id")) {
                    ps.setQueryTimeout(10);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) products.add(new ProductDetails(rs.getLong("id"),rs.getString("name"),rs.getString("brand"),rs.getString("model"),rs.getString("category"),rs.getString("variant"),rs.getString("art")));
                    }
                }
                List<RawListing> listings = new ArrayList<>();
                String sql = "SELECT l.*, s.name AS store_name FROM product_listings l JOIN stores s ON s.id=l.store_id ORDER BY l.id";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setQueryTimeout(10);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) listings.add(new RawListing(rs.getLong("id"),rs.getLong("product_id"),rs.getString("store_name"),rs.getString("title"),rs.getString("model"),rs.getString("variant"),rs.getBigDecimal("price"),rs.getBigDecimal("mrp"),rs.getBigDecimal("rating"),rs.getBigDecimal("delivery_charge"),rs.getString("delivery_estimate"),rs.getBoolean("available"),rs.getString("url")));
                    }
                }
                c.commit();
                return new CatalogSnapshot(products, listings);
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException | RuntimeException e) {
            // Don't expose JDBC URLs, credentials or vendor exception strings in the UI.
            throw new DataAccessException("MySQL catalog could not be loaded. Check that MySQL is running, import schema.sql, and verify local credentials. No demo fallback was used.", e);
        }
    }
}
