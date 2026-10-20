package tulita.photocatalog.model;
import java.math.BigDecimal;
import java.util.*;
/** Curated identity/variant, with genuine, unchanged historical observations. */
public record PhotoProduct(String id,String name,String brand,String model,String variant,String category,String reviewNote,List<Offer> offers,List<Photo> photos){
    public PhotoProduct{offers=List.copyOf(offers);photos=List.copyOf(photos);}
    public record Offer(String store,BigDecimal price,String currency,String title,String url,String observedAt,String sourceFile,int sourceRow,int sourceSide,String sourceRowSha256,String specifications,String description){}
    public record Photo(String path,String sourceUrl,String sha256,String store){}
    public BigDecimal lowest(){return offers.stream().map(Offer::price).min(BigDecimal::compareTo).orElseThrow();}
    public String currency(){return offers.get(0).currency();}
}
