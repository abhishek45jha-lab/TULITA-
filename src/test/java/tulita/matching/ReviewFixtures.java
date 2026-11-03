package tulita.matching;
import tulita.dataset.model.*;
import tulita.matching.model.*;
import tulita.matching.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
/** Fictional unit-test records ONLY. Never written to the distributed catalog/mapping CSV. */
public final class ReviewFixtures {
    public static CanonicalProduct canonical(){return new CanonicalProduct("test-watch","TEST FIXTURE ONLY watch","Demo","TEST900","Watches","black-men","","Black","","","Men");}
    public static DatasetProduct row(String store){return new DatasetProduct(store+"-test", "test", "TEST FIXTURE ONLY Demo TEST900 Black Men Watch","Watches","Watches","Demo",new BigDecimal(store.equals("Amazon")?"900":"1000"),new BigDecimal("1200"),new BigDecimal("4.2"),"2016-01-01","https://example.invalid/fixture","Test only","",new RecordProvenance(store,"fixture-"+store,"https://example.invalid/fixture","TEST ONLY","Not genuine","","","Black","Men",""));}
    public static DatasetSnapshot snapshot(List<DatasetProduct> rows){return new DatasetSnapshot(rows,"TEST FIXTURE ONLY","https://example.invalid","TEST","test","2016","2016","CSV");}
    public static ReviewedLink link(DatasetProduct p){return link(p,canonical());}
    public static ReviewedLink link(DatasetProduct p,CanonicalProduct c){return new ReviewedLink(c,p.store(),p.recordId(),RecordFingerprint.of(p),"https://example.invalid/review",LocalDate.of(2025,1,1),"TEST FIXTURE ONLY: reviewed manufacturer code and exact variant.");}
    public static CanonicalProduct changed(CanonicalProduct c,String model,String color,String capacity){return new CanonicalProduct(c.id(),c.name(),c.brand(),model,c.category(),c.variantKey(),c.size(),color,capacity,c.quantity(),c.gender());}
    public static DatasetProduct title(DatasetProduct p,String title){return new DatasetProduct(p.recordId(),p.productId(),title,p.category(),p.categoryPath(),p.brand(),p.price(),p.mrp(),p.rating(),p.observedAt(),p.url(),p.description(),p.specifications(),p.provenance());}
}
