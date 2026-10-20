package tulita.matching.service;
import tulita.dataset.model.DatasetProduct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
public final class RecordFingerprint {
    private RecordFingerprint(){}
    private static String amount(BigDecimal n){return n==null?"<null>":n.stripTrailingZeros().toPlainString();}
    /** Bind a review to the exact source observation, including prices; SQL decimal scale is irrelevant. */
    public static String ofSqlRow(java.sql.ResultSet rs)throws java.sql.SQLException{
        var m=new tulita.dataset.model.RecordProvenance(rs.getString("store"),rs.getString("source_key"),rs.getString("source_url"),rs.getString("license_name"),rs.getString("date_note"),rs.getString("availability"),rs.getString("variant_size"),rs.getString("color"),rs.getString("gender"),rs.getString("quantity"));
        return of(new DatasetProduct(rs.getString("record_id"),rs.getString("product_id"),rs.getString("title"),rs.getString("category"),rs.getString("category_path"),rs.getString("brand"),rs.getBigDecimal("price"),rs.getBigDecimal("mrp"),rs.getBigDecimal("rating"),rs.getString("observed_at"),rs.getString("product_url"),rs.getString("description"),rs.getString("specifications"),m));
    }
    public static String of(DatasetProduct p){
        var m=p.provenance();String[] values={p.recordId(),p.productId(),p.title(),p.category(),p.categoryPath(),p.description(),p.specifications(),p.brand(),amount(p.price()),amount(p.mrp()),amount(p.rating()),p.observedAt(),p.url(),m.store(),m.sourceKey(),m.sourceUrl(),m.license(),m.dateNote(),m.availability(),m.size(),m.color(),m.gender(),m.quantity()};
        StringBuilder text=new StringBuilder();for(String v:values){String s=v==null?"<null>":v;text.append(s.length()).append(':').append(s);}
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 is unavailable.",e);}
    }
}
