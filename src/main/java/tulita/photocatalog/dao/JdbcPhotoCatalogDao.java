package tulita.photocatalog.dao;
import tulita.photocatalog.model.PhotoProduct;
import tulita.photocatalog.model.PhotoProduct.*;
import tulita.photocatalog.service.CatalogValidator;
import tulita.database.ConnectionFactory;
import tulita.dao.DataAccessException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
public final class JdbcPhotoCatalogDao implements PhotoCatalogDao{
    private final ConnectionFactory factory;private final Path root;
    public JdbcPhotoCatalogDao(ConnectionFactory factory,Path root){this.factory=factory;this.root=root;}
    public List<PhotoProduct> load()throws Exception{
        try(Connection c=factory.open()){
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            try{
                Map<String,List<Offer>> offers=new HashMap<>();Map<String,List<Photo>> images=new HashMap<>();
                try(var ps=c.prepareStatement("SELECT * FROM photo_offers ORDER BY product_id,store")){ps.setQueryTimeout(15);try(var rs=ps.executeQuery()){while(rs.next())offers.computeIfAbsent(rs.getString("product_id"),k->new ArrayList<>()).add(new Offer(rs.getString("store"),rs.getBigDecimal("price"),rs.getString("currency"),rs.getString("source_title"),rs.getString("source_url"),rs.getString("observed_at"),rs.getString("source_file"),rs.getInt("source_row"),rs.getInt("source_side"),rs.getString("source_row_sha256"),rs.getString("specifications"),rs.getString("description")));}}
                try(var ps=c.prepareStatement("SELECT * FROM photo_images ORDER BY product_id,relative_path")){ps.setQueryTimeout(15);try(var rs=ps.executeQuery()){while(rs.next())images.computeIfAbsent(rs.getString("product_id"),k->new ArrayList<>()).add(new Photo(rs.getString("relative_path"),rs.getString("source_url"),rs.getString("sha256"),rs.getString("store")));}}
                List<PhotoProduct> data=new ArrayList<>();try(var ps=c.prepareStatement("SELECT * FROM photo_products ORDER BY id")){ps.setQueryTimeout(15);try(var rs=ps.executeQuery()){while(rs.next()){String id=rs.getString("id");data.add(new PhotoProduct(id,rs.getString("name"),rs.getString("brand"),rs.getString("model"),rs.getString("variant"),rs.getString("category"),rs.getString("review_note"),offers.getOrDefault(id,List.of()),images.getOrDefault(id,List.of())));}}}
                CatalogValidator.validate(data,root);c.commit();return List.copyOf(data);
            }catch(Exception e){c.rollback();throw e;}
        }catch(Exception e){throw new DataAccessException("Could not load photo catalog from MySQL. Run photo-catalog-schema.sql and PhotoCatalogImportMain; keep datasets/promap intact. No JSON/demo fallback was used.",e);}
    }
    public void replace(List<PhotoProduct> data)throws Exception{
        CatalogValidator.validate(data,root);
        try(Connection c=factory.open()){
            c.setAutoCommit(false);try{
                try(var ps=c.prepareStatement("DELETE FROM photo_products")){ps.setQueryTimeout(15);ps.executeUpdate();}
                try(var ps=c.prepareStatement("INSERT INTO photo_products VALUES(?,?,?,?,?,?,?)")){
                    for(var p:data){String[] v={p.id(),p.name(),p.brand(),p.model(),p.variant(),p.category(),p.reviewNote()};for(int i=0;i<v.length;i++)ps.setString(i+1,v[i]);ps.addBatch();}ps.executeBatch();}
                try(var ps=c.prepareStatement("INSERT INTO photo_offers VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")){
                    for(var p:data)for(var o:p.offers()){ps.setString(1,p.id());ps.setString(2,o.store());ps.setBigDecimal(3,o.price());ps.setString(4,o.currency());ps.setString(5,o.title());ps.setString(6,o.url());ps.setString(7,o.observedAt());ps.setString(8,o.sourceFile());ps.setInt(9,o.sourceRow());ps.setInt(10,o.sourceSide());ps.setString(11,o.sourceRowSha256());ps.setString(12,o.specifications());ps.setString(13,o.description());ps.addBatch();}ps.executeBatch();}
                try(var ps=c.prepareStatement("INSERT INTO photo_images VALUES(?,?,?,?,?)")){
                    for(var p:data)for(var i:p.photos()){ps.setString(1,p.id());ps.setString(2,i.path());ps.setString(3,i.sourceUrl());ps.setString(4,i.sha256());ps.setString(5,i.store());ps.addBatch();}ps.executeBatch();}
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }catch(Exception e){throw new DataAccessException("Photo catalog import failed and was rolled back. Check schema and three photo-table DML grants. No partial catalog accepted.",e);}
    }
}
