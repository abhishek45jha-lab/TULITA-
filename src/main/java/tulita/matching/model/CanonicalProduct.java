package tulita.matching.model;
import tulita.matcher.ProductMatcher;
/** One manufacturer model and one exact declared variant. No prices are stored here. */
public record CanonicalProduct(String id,String name,String brand,String model,String category,
        String variantKey,String size,String color,String capacity,String quantity,String gender) {
    public String key(){return id+"::"+new ProductMatcher().normalize(variantKey);}
}
