package tulita.dataset.dao;
import tulita.dataset.model.*;
import tulita.database.ConnectionFactory;
import tulita.dao.DataAccessException;
import java.sql.*;
import java.util.*;

/** Separate three-store historical schema; replacement is one atomic transaction. */
public final class MultiJdbcDao implements DatasetDao {
    private final ConnectionFactory factory;
    public MultiJdbcDao(ConnectionFactory factory){this.factory=factory;}
    @Override public DatasetSnapshot load()throws DataAccessException{
        try(Connection c=factory.open()){
            c.setReadOnly(true);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            try{
                int expected=0;String hash="";Map<String,RecordProvenance> sources=new HashMap<>();
                try(PreparedStatement ps=c.prepareStatement("SELECT * FROM multi_sources")){ps.setQueryTimeout(15);try(ResultSet rs=ps.executeQuery()){while(rs.next()){expected+=rs.getInt("row_count");hash=rs.getString("sha256");String key=rs.getString("source_key");sources.put(key,new RecordProvenance(rs.getString("store"),key,rs.getString("source_url"),rs.getString("license_name"),rs.getString("date_note"),"","","","",""));}}}
                List<DatasetProduct> rows=new ArrayList<>();
                try(PreparedStatement ps=c.prepareStatement("SELECT * FROM multi_records ORDER BY record_id")){
                    ps.setQueryTimeout(60);ps.setFetchSize(Integer.MIN_VALUE);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                        var source=sources.get(rs.getString("source_key"));if(source==null)throw new SQLException("Missing source metadata");
                        var meta=new RecordProvenance(source.store(),source.sourceKey(),source.sourceUrl(),source.license(),source.dateNote(),rs.getString("availability"),rs.getString("variant_size"),rs.getString("color"),rs.getString("gender"),rs.getString("quantity"));
                        rows.add(new DatasetProduct(rs.getString("record_id"),rs.getString("product_id"),rs.getString("title"),rs.getString("category"),rs.getString("category_path"),rs.getString("brand"),rs.getBigDecimal("price"),rs.getBigDecimal("mrp"),rs.getBigDecimal("rating"),rs.getString("observed_at"),rs.getString("product_url"),rs.getString("description"),rs.getString("specifications"),meta));
                    }}
                }
                if(rows.isEmpty()||rows.size()!=expected)throw new SQLException("Incomplete import");
                c.commit();return new DatasetSnapshot(rows,"Amazon + Flipkart + Myntra","See datasets/multistore/ATTRIBUTION.md","Per-source CC BY-SA / ODC-By / CC0",hash,"2015–2016 Flipkart","2021 Myntra / 2023 Amazon","MySQL");
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}
        }catch(SQLException|RuntimeException ex){throw new DataAccessException("Could not read the three-store MySQL catalog. Import multistore-schema.sql, configure credentials and run MultiImportMain. No demo/CSV fallback was used.",ex);}
    }
    public void replace(DatasetSnapshot data)throws DataAccessException{
        if(data.products().isEmpty())throw new DataAccessException("Cannot import an empty catalog.",null);
        try(Connection c=factory.open()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM multi_records")){ps.setQueryTimeout(60);ps.executeUpdate();}
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM multi_sources")){ps.setQueryTimeout(60);ps.executeUpdate();}
                Map<String,List<DatasetProduct>> groups=new LinkedHashMap<>();data.products().forEach(p->groups.computeIfAbsent(p.provenance().sourceKey(),k->new ArrayList<>()).add(p));
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO multi_sources(source_key,store,source_url,license_name,date_note,row_count,sha256) VALUES(?,?,?,?,?,?,?)")){
                    ps.setQueryTimeout(30);for(var group:groups.values()){var m=group.get(0).provenance();ps.setString(1,m.sourceKey());ps.setString(2,m.store());ps.setString(3,m.sourceUrl());ps.setString(4,m.license());ps.setString(5,m.dateNote());ps.setInt(6,group.size());ps.setString(7,data.sha256());ps.addBatch();}ps.executeBatch();
                }
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO multi_records(record_id,source_key,product_id,title,category,category_path,brand,price,mrp,rating,observed_at,product_url,description,specifications,availability,variant_size,color,gender,quantity) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
                    ps.setQueryTimeout(60);int count=0;
                    for(var p:data.products()){
                        ps.setString(1,p.recordId());ps.setString(2,p.provenance().sourceKey());ps.setString(3,p.productId());ps.setString(4,p.title());ps.setString(5,p.category());ps.setString(6,p.categoryPath());ps.setString(7,p.brand());ps.setBigDecimal(8,p.price());ps.setBigDecimal(9,p.mrp());ps.setBigDecimal(10,p.rating());ps.setString(11,p.observedAt());ps.setString(12,p.url());ps.setString(13,p.description());ps.setString(14,p.specifications());ps.setString(15,p.provenance().availability());ps.setString(16,p.provenance().size());ps.setString(17,p.provenance().color());ps.setString(18,p.provenance().gender());ps.setString(19,p.provenance().quantity());ps.addBatch();if(++count%500==0)ps.executeBatch();
                    }if(count%500!=0)ps.executeBatch();
                }c.commit();
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}
        }catch(SQLException|RuntimeException ex){throw new DataAccessException("Three-store import failed. Check multistore-schema.sql and SELECT/INSERT/UPDATE/DELETE grants on multi_sources and multi_records. The transaction was rolled back; no partial import was accepted.",ex);}
    }
}
