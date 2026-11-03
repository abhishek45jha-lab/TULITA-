package tulita.dataset;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tulita.dao.DataAccessException;
import tulita.dataset.dao.*;
import tulita.dataset.model.*;
import tulita.dataset.service.DatasetService;
import java.nio.file.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class DatasetTest {
    private static DatasetSnapshot data;
    private static DatasetService service;
    @TempDir Path temp;
    @BeforeAll static void loadBundledDataset() throws Exception {
        data=new CsvDatasetDao(Path.of("datasets/flipkart_com-ecommerce_sample.csv")).load(); service=new DatasetService(data);
    }
    private DatasetService.Page search(String q,String c,DatasetService.Sort sort,int page) { return service.search(q,c,sort,false,Set.of(),page,12); }
    @Test void originalFileHas20000IndependentRecords() { assertEquals(20000,data.products().size()); assertEquals(20000,data.products().stream().map(DatasetProduct::recordId).distinct().count()); assertEquals(19998,data.products().stream().map(DatasetProduct::productId).distinct().count()); }
    @Test void originalHashAndProvenancePreserved() { assertEquals("56f8f699c9e847356666c2eab3c3ab1244340f6a98ad08e39ea2199ebe993ad1",data.sha256()); assertEquals("CC BY-SA 4.0",data.license()); assertTrue(data.firstObserved().startsWith("2015-12-01")); assertTrue(data.lastObserved().startsWith("2016-06-28")); }
    @Test void missingSourceFieldsStayUnknown() { assertEquals(78,data.products().stream().filter(p->p.price()==null).count()); assertEquals(18151,data.products().stream().filter(p->p.rating()==null).count()); }
    @Test void fullCatalogHasPagination() { var p=search("","All categories",DatasetService.Sort.LOWEST_PRICE,0); assertEquals(20000,p.total()); assertEquals(12,p.items().size()); assertEquals(1667,p.pages()); }
    @Test void lastPageAndOutOfRangeClamp() { var p=search("","All categories",DatasetService.Sort.LOWEST_PRICE,999999); assertEquals(1666,p.page()); assertEquals(8,p.items().size()); }
    @Test void pageBoundariesHaveNoDuplicates() { var a=search("","All categories",DatasetService.Sort.LOWEST_PRICE,0); var b=search("","All categories",DatasetService.Sort.LOWEST_PRICE,1); assertTrue(a.items().stream().noneMatch(x->b.items().contains(x))); }
    @Test void realDatasetKeywordSearchIsCaseInsensitive() { var a=search("SaMsUnG","All categories",DatasetService.Sort.LOWEST_PRICE,0); var b=search("samsung","All categories",DatasetService.Sort.LOWEST_PRICE,0); assertTrue(a.total()>20); assertEquals(a.total(),b.total()); }
    @Test void partialMultiWordSearch() { var p=search("women short","Clothing",DatasetService.Sort.LOWEST_PRICE,0); assertTrue(p.total()>0); assertTrue(p.items().stream().allMatch(x->x.title().toLowerCase(Locale.ROOT).contains("women"))); }
    @Test void categoryExtraction() { assertTrue(service.categories().containsAll(List.of("Clothing","Footwear","Mobiles & Accessories","Watches"))); var p=search("","Footwear",DatasetService.Sort.LOWEST_PRICE,0); assertEquals(1227,p.total()); assertTrue(p.items().stream().allMatch(x->x.category().equals("Footwear"))); }
    @Test void sortsUseHistoricalMetricsAndMissingLast() {
        for(var sort:DatasetService.Sort.values()) {
            var all=service.search("","All categories",sort,false,Set.of(),0,30000).items();
            BigDecimal previous=null; boolean nullSeen=false;
            for(var p:all) {
                BigDecimal n=switch(sort){case LOWEST_PRICE->p.price();case HIGHEST_DISCOUNT->p.discount().orElse(null);case TOP_RATED->p.rating();};
                if(n==null){nullSeen=true;continue;} assertFalse(nullSeen);
                if(previous!=null) assertTrue(sort==DatasetService.Sort.LOWEST_PRICE?previous.compareTo(n)<=0:previous.compareTo(n)>=0); previous=n;
            }
        }
    }
    @Test void watchlistIdsKeepObservationsSeparate() { String id=data.products().get(0).recordId(); var p=service.search("","All categories",DatasetService.Sort.LOWEST_PRICE,true,Set.of(id),0,12); assertEquals(1,p.total()); assertEquals(id,p.items().get(0).recordId()); }
    @Test void emptyResultsHaveOneEmptyPage() { var p=search("zzzz-no-such-product-123","All categories",DatasetService.Sort.LOWEST_PRICE,10); assertEquals(0,p.total()); assertEquals(1,p.pages()); assertTrue(p.items().isEmpty()); }
    @Test void wrongCsvAndMissingFileDoNotFallback() throws Exception { Path p=temp.resolve("wrong.csv"); Files.writeString(p,"wrong,headers\n1,2\n"); assertThrows(DataAccessException.class,()->new CsvDatasetDao(p).load()); assertThrows(DataAccessException.class,()->new CsvDatasetDao(temp.resolve("missing.csv")).load()); }
    @Test void csvHandlesEmbeddedCommasQuotesAndMultiline() throws Exception {
        String header="uniq_id,crawl_timestamp,product_url,product_name,product_category_tree,pid,retail_price,discounted_price,product_rating,brand,description,product_specifications\n";
        Path p=temp.resolve("quoted.csv"); Files.writeString(p,header+"id,2016-01-01 00:00:00 +0000,http://www.flipkart.com/p,\"A, B \"\"special\"\"\",Clothing,pid,100,80,No rating available,Brand,\"line one\nline two\",specs\n");
        var product=new CsvDatasetDao(p).load().products().get(0); assertEquals("A, B \"special\"",product.title()); assertEquals("line one\nline two",product.description()); assertNull(product.rating());
    }
    @Test void archivedLinksUpgradedWithoutAllowingArbitraryHosts() { assertEquals("https://www.flipkart.com/x?pid=123",DatasetLinks.safeProductUrl("http://www.flipkart.com/x?pid=123").orElseThrow()); for(String s:List.of("javascript:alert(1)","http://flipkart.com.evil.com/x","https://user@flipkart.com/x"))assertTrue(DatasetLinks.safeProductUrl(s).isEmpty()); }
    @Test void invalidPageSizeRejected() { assertThrows(IllegalArgumentException.class,()->service.search("","All categories",DatasetService.Sort.LOWEST_PRICE,false,Set.of(),0,0)); }
}
