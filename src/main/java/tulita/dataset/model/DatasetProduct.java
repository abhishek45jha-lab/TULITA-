package tulita.dataset.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** An original historical observation, not a canonical matched product or live offer. */
public record DatasetProduct(String recordId, String productId, String title, String category,
        String categoryPath, String brand, BigDecimal price, BigDecimal mrp, BigDecimal rating,
        String observedAt, String url, String description, String specifications, RecordProvenance provenance) {
    public DatasetProduct(String recordId,String productId,String title,String category,String categoryPath,String brand,BigDecimal price,BigDecimal mrp,BigDecimal rating,String observedAt,String url,String description,String specifications) {
        this(recordId,productId,title,category,categoryPath,brand,price,mrp,rating,observedAt,url,description,specifications,RecordProvenance.flipkart());
    }
    public String store(){return provenance.store();}
    public DatasetProduct {
        if (recordId == null || recordId.isBlank() || title == null || title.isBlank())
            throw new IllegalArgumentException("Historical record needs an ID and title.");
    }
    public Optional<BigDecimal> discount() {
        if (price == null || mrp == null || mrp.signum() <= 0 || mrp.compareTo(price)<0) return Optional.empty();
        return Optional.of(mrp.subtract(price).multiply(new BigDecimal("100")).divide(mrp,0,RoundingMode.HALF_UP));
    }
    public String observedDate() {
        try { return OffsetDateTime.parse(observedAt,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")).toLocalDate().toString(); }
        catch (RuntimeException e) { return observedAt.isBlank() ? "Unknown" : observedAt; }
    }
    public String art() {
        String c=category.toLowerCase(java.util.Locale.ROOT);
        if(c.contains("footwear")) return "shoe";
        if(c.contains("mobile")) return "phone";
        if(c.contains("watch")) return "watch";
        if(c.contains("bag")) return "bag";
        if(c.contains("computer")) return "mouse";
        if(c.contains("eyewear")) return "glasses";
        if(c.contains("clothing")) return "shirt";
        return "catalog";
    }
}
