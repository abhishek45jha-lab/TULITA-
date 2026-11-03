package tulita;

import org.junit.jupiter.api.*;
import tulita.dao.*;
import tulita.database.*;
import tulita.matcher.*;
import tulita.model.*;
import tulita.parser.*;
import tulita.service.*;
import tulita.util.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    private CatalogService catalog;
    private final WatchlistService watch = new WatchlistService();
    private final ProductMatcher matcher = new ProductMatcher();
    private final ComparisonService compare = new ComparisonService();
    @BeforeEach void setup() throws Exception { catalog = new CatalogService(new DemoCatalogDao().load()); }
    private Listing listing(long id, String price, String mrp, String shipping, boolean available) {
        return new Listing(new RawListing(id,7,"Amazon","Nike Pegasus 40 Running Shoes","Pegasus 40","UK 9 / White",new BigDecimal(price),new BigDecimal(mrp),new BigDecimal("4.3"),shipping==null?null:new BigDecimal(shipping),"2–4 days",available,""));
    }
    private List<ProductDetails> search(String q) { return catalog.search(q,"All products",CatalogService.Sort.LOWEST_PRICE,false,watch); }
    @Test void entireSeedIsComparable() { assertEquals(20,catalog.productCount()); assertEquals(54,catalog.listingCount()); assertEquals(0,catalog.excludedCount()); }
    @Test void partialCaseInsensitiveAndMultiWordSearch() { assertEquals(1,search("pEgAsUs").size()); assertEquals("Nike Air Zoom Pegasus 40",search("nike 40").get(0).getName()); assertFalse(search("backp").isEmpty()); }
    @Test void punctuationAndBlankSearch() { assertEquals(20,search("   ").size()); assertEquals(1,search("sony wh-ch720n").size()); }
    @Test void emptySearchResult() { assertTrue(search("nonexistent123").isEmpty()); }
    @Test void categoryFilter() { var list=catalog.search("","Footwear",CatalogService.Sort.LOWEST_PRICE,false,watch); assertEquals(5,list.size()); assertTrue(list.stream().allMatch(p->p.getCategory().equals("Footwear"))); }
    @Test void explainableTitleSimilarity() { assertTrue(matcher.potentiallySimilar("Nike Air Zoom Pegasus 40","Nike Pegasus 40 Running Shoes")); }
    @Test void differentModelNumberRejected() { assertFalse(matcher.potentiallySimilar("Nike Air Zoom Pegasus 40","Nike Pegasus 41 Running Shoes")); }
    @Test void differentModelQualifierRejected() { assertFalse(matcher.potentiallySimilar("Apple iPhone 15","Apple iPhone 15 Pro")); }
    @Test void exactVariantIsMandatory() {
        ProductDetails p=new ProductDetails(7,"Nike Air Zoom Pegasus 40","Nike","Pegasus 40","Footwear","UK 8 / White","shoe");
        assertFalse(matcher.comparable(p,listing(1,"100","200","0",true)));
    }
    @Test void storageMismatchIsRejected() {
        ProductDetails p=new ProductDetails(7,"Apple iPhone 15","Apple","iPhone 15","Electronics","128GB / Black","phone");
        Listing l=new Listing(new RawListing(1,7,"Amazon","Apple iPhone 15","iPhone 15","256GB / Black",new BigDecimal("50000"),new BigDecimal("60000"),new BigDecimal("4.5"),BigDecimal.ZERO,"",true,""));
        assertFalse(matcher.comparable(p,l));
    }
    @Test void unsafeListingsAreExcludedFromCatalog() {
        ProductDetails p=new ProductDetails(7,"Nike Air Zoom Pegasus 40","Nike","Pegasus 41","Footwear","UK 9 / White","shoe");
        Listing l=listing(1,"100","200","0",true);
        RawListing raw=new RawListing(1,7,"Amazon",l.getTitle(),l.getModel(),l.getVariant(),l.getPrice(),l.getMrp(),l.getRating(),l.getDelivery(),"",true,"");
        CatalogService c=new CatalogService(new CatalogDao.CatalogSnapshot(List.of(p),List.of(raw)));
        assertEquals(1,c.excludedCount()); assertTrue(c.listings(p).isEmpty());
    }
    @Test void finalCostNotHighestDiscountWins() {
        Listing highDiscount=listing(1,"100","1000","79",true), lowTotal=listing(2,"140","200","0",true);
        assertTrue(highDiscount.getDiscount().compareTo(lowTotal.getDiscount())>0);
        assertTrue(compare.isBest(lowTotal,List.of(highDiscount,lowTotal))); assertFalse(compare.isBest(highDiscount,List.of(highDiscount,lowTotal)));
    }
    @Test void outOfStockIsNeverBest() { assertFalse(compare.isBest(listing(1,"1","200","0",false),List.of(listing(1,"1","200","0",false),listing(2,"140","200","0",true)))); }
    @Test void unknownDeliveryIsNotInvented() {
        Listing unknown=listing(1,"1","200",null,true), known=listing(2,"140","200","0",true);
        assertTrue(unknown.finalPrice().isEmpty()); assertFalse(compare.isBest(unknown,List.of(unknown,known)));
        assertTrue(compare.hasUnknownAvailableTotal(List.of(unknown,known))); assertTrue(compare.isBest(known,List.of(unknown,known)));
    }
    @Test void tiesBothHighlighted() { var a=listing(1,"100","200","40",true); var b=listing(2,"140","200","0",true); assertTrue(compare.isBest(a,List.of(a,b))); assertTrue(compare.isBest(b,List.of(a,b))); }
    @Test void emptyComparisonHasNoWinner() { assertTrue(compare.lowestKnownTotal(List.of()).isEmpty()); }
    @Test void decimalArithmeticAndDiscount() { var l=listing(1,"99.95","199.90","0.10",true); assertEquals(new BigDecimal("100.05"),l.finalPrice().orElseThrow()); assertEquals(new BigDecimal("50"),l.getDiscount()); }
    @Test void invalidMoneyRejected() { assertThrows(IllegalArgumentException.class,()->listing(1,"-1","200","0",true)); assertThrows(IllegalArgumentException.class,()->listing(1,"300","200","0",true)); }
    @Test void polymorphicParsersValidateStore() { StoreParser p=new FlipkartParser(); assertEquals("Flipkart",p.storeName()); assertThrows(IllegalArgumentException.class,()->p.parse(new RawListing(1,1,"Amazon","","","",BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ZERO,"",true,""))); }
    @Test void watchlistSessionToggle() { ProductDetails p=search("sony").get(0); assertTrue(watch.toggle(p.getId())); assertEquals(1,catalog.search("","All products",CatalogService.Sort.LOWEST_PRICE,true,watch).size()); assertFalse(watch.toggle(p.getId())); assertEquals(0,watch.size()); }
    @Test void allSortsUseBestOfferMetrics() {
        for (var sort:CatalogService.Sort.values()) {
            var list=catalog.search("","All products",sort,false,watch);
            for(int i=1;i<list.size();i++) {
                Listing a=catalog.best(list.get(i-1)).orElseThrow(), b=catalog.best(list.get(i)).orElseThrow();
                if(sort==CatalogService.Sort.LOWEST_PRICE) assertTrue(a.finalPrice().orElseThrow().compareTo(b.finalPrice().orElseThrow())<=0);
                if(sort==CatalogService.Sort.HIGHEST_DISCOUNT) assertTrue(a.getDiscount().compareTo(b.getDiscount())>=0);
                if(sort==CatalogService.Sort.TOP_RATED) assertTrue(a.getRating().compareTo(b.getRating())>=0);
            }
        }
    }
    @Test void linksOnlyAllowHttpsAndMatchingStoreHost() {
        assertTrue(StoreLinks.isAllowed("Amazon","https://www.amazon.in/s?k=nike"));
        for(String url:List.of("javascript:alert(1)","file:///tmp/a","https://amazon.in.evil.com/x","http://amazon.in","https://evil@amazon.in")) assertFalse(StoreLinks.isAllowed("Amazon",url));
        assertFalse(StoreLinks.isAllowed("Flipkart","https://amazon.in"));
    }
    @Test void configToStringNeverLeaksCredentials() { assertFalse(new AppConfig("mysql","sensitive-url","user","secret").toString().contains("secret")); }
    @Test void jdbcFailureHasActionableSanitizedMessage() {
        var config=new AppConfig("mysql","jdbc:mysql://127.0.0.1:1/tulita?connectTimeout=1000","test","never-print-this");
        var ex=assertThrows(DataAccessException.class,()->new JdbcCatalogDao(new ConnectionFactory(config)).load());
        assertTrue(ex.getMessage().contains("schema.sql")); assertFalse(ex.getMessage().contains("never-print-this")); assertTrue(ex.getMessage().contains("No demo fallback"));
    }
}
