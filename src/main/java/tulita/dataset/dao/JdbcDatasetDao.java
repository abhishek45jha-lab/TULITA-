package tulita.dataset.dao;

import tulita.dao.DataAccessException;
import tulita.database.ConnectionFactory;
import tulita.dataset.model.*;
import java.sql.*;
import java.util.*;

/** Historical records live in their own tables and cannot mix with fictional demo listings. */
public final class JdbcDatasetDao implements DatasetDao {
    private final ConnectionFactory factory;
    public JdbcDatasetDao(ConnectionFactory factory) { this.factory=factory; }
    @Override public DatasetSnapshot load() throws DataAccessException {
        try(Connection c=factory.open()) {
            c.setReadOnly(true); c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
            try {
                String title,url,license,sha,first,last; int count;
                try(PreparedStatement ps=c.prepareStatement("SELECT * FROM dataset_sources WHERE source_key=?")) {
                    ps.setString(1,DatasetSnapshot.SOURCE_KEY); ps.setQueryTimeout(15);
                    try(ResultSet rs=ps.executeQuery()) {
                        if(!rs.next()) throw new SQLException("Dataset not imported.");
                        title=rs.getString("title"); url=rs.getString("source_url"); license=rs.getString("license_name"); sha=rs.getString("sha256");
                        first=rs.getString("first_observed"); last=rs.getString("last_observed"); count=rs.getInt("row_count");
                    }
                }
                List<DatasetProduct> products=new ArrayList<>();
                try(PreparedStatement ps=c.prepareStatement("SELECT * FROM dataset_products WHERE source_key=? ORDER BY record_id")) {
                    ps.setString(1,DatasetSnapshot.SOURCE_KEY); ps.setQueryTimeout(30);
                    try(ResultSet rs=ps.executeQuery()) {
                        while(rs.next()) products.add(new DatasetProduct(rs.getString("record_id"),rs.getString("product_id"),rs.getString("title"),rs.getString("category"),rs.getString("category_path"),rs.getString("brand"),rs.getBigDecimal("price"),rs.getBigDecimal("mrp"),rs.getBigDecimal("rating"),rs.getString("observed_at"),rs.getString("product_url"),rs.getString("description"),rs.getString("specifications")));
                    }
                }
                if(products.size()!=count || count==0) throw new SQLException("Incomplete dataset snapshot.");
                c.commit(); return new DatasetSnapshot(products,title,url,license,sha,first,last,"MySQL");
            } catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch(SQLException | RuntimeException e) {
            throw new DataAccessException("Historical MySQL catalog could not be loaded. Check MySQL/credentials, import dataset-schema.sql and run the Java dataset importer. No CSV or demo fallback was used.",e);
        }
    }
    /** Replaces only this source, in one transaction. Failure retains the previous complete import. */
    public void replace(DatasetSnapshot data) throws DataAccessException {
        try(Connection c=factory.open()) {
            c.setAutoCommit(false);
            try {
                String sql="INSERT INTO dataset_sources(source_key,title,source_url,license_name,sha256,row_count,first_observed,last_observed) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),source_url=VALUES(source_url),license_name=VALUES(license_name),sha256=VALUES(sha256),row_count=VALUES(row_count),first_observed=VALUES(first_observed),last_observed=VALUES(last_observed),imported_at=CURRENT_TIMESTAMP";
                try(PreparedStatement ps=c.prepareStatement(sql)) {
                    ps.setQueryTimeout(30); ps.setString(1,DatasetSnapshot.SOURCE_KEY); ps.setString(2,data.sourceTitle()); ps.setString(3,data.sourceUrl()); ps.setString(4,data.license()); ps.setString(5,data.sha256()); ps.setInt(6,data.products().size()); ps.setString(7,data.firstObserved()); ps.setString(8,data.lastObserved()); ps.executeUpdate();
                }
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM dataset_products WHERE source_key=?")) {
                    ps.setString(1,DatasetSnapshot.SOURCE_KEY); ps.setQueryTimeout(30); ps.executeUpdate();
                }
                sql="INSERT INTO dataset_products(source_key,record_id,product_id,title,category,category_path,brand,price,mrp,rating,observed_at,product_url,description,specifications) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                try(PreparedStatement ps=c.prepareStatement(sql)) {
                    ps.setQueryTimeout(60); int batch=0;
                    for(DatasetProduct p:data.products()) {
                        ps.setString(1,DatasetSnapshot.SOURCE_KEY); ps.setString(2,p.recordId()); ps.setString(3,p.productId()); ps.setString(4,p.title()); ps.setString(5,p.category()); ps.setString(6,p.categoryPath()); ps.setString(7,p.brand()); ps.setBigDecimal(8,p.price()); ps.setBigDecimal(9,p.mrp()); ps.setBigDecimal(10,p.rating()); ps.setString(11,p.observedAt()); ps.setString(12,p.url()); ps.setString(13,p.description()); ps.setString(14,p.specifications()); ps.addBatch();
                        if(++batch%500==0) ps.executeBatch();
                    }
                    if(batch%500!=0) ps.executeBatch();
                }
                c.commit();
            } catch(SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch(SQLException | RuntimeException e) {
            throw new DataAccessException("Historical dataset import failed. Verify dataset-schema.sql, credentials and SELECT/INSERT/UPDATE/DELETE permissions on the two dataset tables. The transaction was rolled back; no partial import was accepted.",e);
        }
    }
}
