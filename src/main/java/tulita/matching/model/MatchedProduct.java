package tulita.matching.model;
import tulita.dataset.model.DatasetProduct;
import java.util.*;
/** The map has at most one source record per store, for exactly one canonical variant. */
public record MatchedProduct(CanonicalProduct product,Map<String,DatasetProduct> listings,
        Map<String,ReviewedLink> reviews) {
    public MatchedProduct{listings=Map.copyOf(listings);reviews=Map.copyOf(reviews);}
    public boolean allThree(){return listings.keySet().containsAll(Set.of("Amazon","Flipkart","Myntra"));}
}
