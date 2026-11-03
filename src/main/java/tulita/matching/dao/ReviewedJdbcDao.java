package tulita.matching.dao;
import tulita.matching.model.*;
import tulita.matching.service.*;
import tulita.dataset.dao.MultiJdbcDao;
import tulita.dataset.model.DatasetSnapshot;
import tulita.database.ConnectionFactory;
import tulita.dao.DataAccessException;
import java.sql.*;
import java.util.*;

public final class ReviewedJdbcDao {
    private final ConnectionFactory factory;
    public ReviewedJdbcDao(ConnectionFactory factory){this.factory=factory;}
    public List<ReviewedLink> load()throws DataAccessException{
        try(Connection c=factory.open();PreparedStatement ps=c.prepareStatement("SELECT l.*,v.* FROM same_product_links l JOIN canonical_variants v ON l.group_key=v.group_key ORDER BY l.group_key,l.store")){
            ps.setQueryTimeout(15);try(ResultSet rs=ps.executeQuery()){List<ReviewedLink> rows=new ArrayList<>();while(rs.next()){
                var p=new CanonicalProduct(rs.getString("canonical_id"),rs.getString("canonical_name"),rs.getString("brand"),rs.getString("manufacturer_model"),rs.getString("category"),rs.getString("variant_key"),rs.getString("size_value"),rs.getString("color_value"),rs.getString("capacity_value"),rs.getString("quantity_value"),rs.getString("gender_value"));
                rows.add(new ReviewedLink(p,rs.getString("store"),rs.getString("record_id"),rs.getString("record_sha256"),rs.getString("evidence_url"),rs.getDate("reviewed_on").toLocalDate(),rs.getString("review_note")));
            }return List.copyOf(rows);}
        }catch(SQLException|RuntimeException ex){throw new DataAccessException("Could not read same-product mappings. Run same-product-schema.sql and grant SELECT on canonical_variants and same_product_links. No candidate/similarity fallback was used.",ex);}
    }
    public void replace(List<ReviewedLink> rows)throws DataAccessException{
        if(rows.isEmpty())throw new DataAccessException("The mapping file has no reviewed links. Empty files are not imported or used to erase existing mappings.",null);
        // Validate against the database, not a different local CSV catalog.
        DatasetSnapshot actual=new MultiJdbcDao(factory).load();
        try{new SameProductService(actual,rows);}catch(IllegalArgumentException ex){throw new DataAccessException("Mapping validation failed: "+ex.getMessage()+" Existing mappings were not changed.",ex);}
        try(Connection c=factory.open()){
            c.setAutoCommit(false);
            try{
                // Recheck each pinned observation inside this transaction before accepting it.
                try(PreparedStatement ps=c.prepareStatement("SELECT r.*,s.store,s.source_url,s.license_name,s.date_note FROM multi_records r JOIN multi_sources s ON r.source_key=s.source_key WHERE r.record_id=? FOR SHARE")){
                    ps.setQueryTimeout(15);
                    for(var row:rows){ps.setString(1,row.recordId());try(ResultSet rs=ps.executeQuery()){if(!rs.next()||!RecordFingerprint.ofSqlRow(rs).equals(row.recordSha256()))throw new SQLException("Source observation changed during review import");}}

                }
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM same_product_links")){ps.executeUpdate();}
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM canonical_variants")){ps.executeUpdate();}
                Set<String> definitions=new HashSet<>();
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO canonical_variants(group_key,canonical_id,canonical_name,brand,manufacturer_model,category,variant_key,size_value,color_value,capacity_value,quantity_value,gender_value) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")){
                    for(var link:rows){var p=link.product();if(!definitions.add(p.key()))continue;String[] values={p.key(),p.id(),p.name(),p.brand(),p.model(),p.category(),p.variantKey(),p.size(),p.color(),p.capacity(),p.quantity(),p.gender()};for(int i=0;i<values.length;i++)ps.setString(i+1,values[i]);ps.addBatch();}ps.executeBatch();
                }
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO same_product_links(group_key,store,record_id,record_sha256,evidence_url,reviewed_on,review_note) VALUES(?,?,?,?,?,?,?)")){
                    for(var link:rows){ps.setString(1,link.product().key());ps.setString(2,link.store());ps.setString(3,link.recordId());ps.setString(4,link.recordSha256());ps.setString(5,link.evidenceUrl());ps.setDate(6,java.sql.Date.valueOf(link.reviewedOn()));ps.setString(7,link.reviewNote());ps.addBatch();}ps.executeBatch();
                }c.commit();
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}
        }catch(SQLException|RuntimeException ex){throw new DataAccessException("Mapping import failed. Check same-product-schema.sql and mapping-table DML grants. The transaction was rolled back; existing mappings/prices were not partially replaced.",ex);}
    }
    private boolean equal(java.math.BigDecimal a,java.math.BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
}
