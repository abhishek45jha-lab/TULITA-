package tulita.dataset;
import org.junit.jupiter.api.*;
import tulita.dataset.dao.*;
import tulita.dataset.model.*;
import tulita.dataset.service.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MultiStoreTest {
    static DatasetSnapshot data;static DatasetService service;
    final CrossStoreMatcher matcher=new CrossStoreMatcher();
    @BeforeAll static void load()throws Exception{data=new MultiCsvDao(MultiCsvDao.defaultPath()).load();service=new DatasetService(data);}
    DatasetProduct fixture(String store,String title,String brand,String size,String color,String gender){return new DatasetProduct(store+title,"id",title,"Footwear","Footwear",brand,new BigDecimal("100"),new BigDecimal("200"),null,"","","","",new RecordProvenance(store,"source","https://example.com","test","Unknown date","Unknown",size,color,gender,""));}
    @Test void countsAndIdentity(){assertEquals(52491,data.products().size());assertEquals(52491,data.products().stream().map(DatasetProduct::recordId).distinct().count());}
    @Test void perStoreFilter(){for(var pair:Map.of("Amazon",20000,"Flipkart",20000,"Myntra",12491).entrySet()){var page=service.search("","All categories",DatasetService.Sort.LOWEST_PRICE,false,Set.of(),0,12,pair.getKey());assertEquals(pair.getValue().intValue(),page.total());assertTrue(page.items().stream().allMatch(p->p.store().equals(pair.getKey())));}}
    @Test void checksum(){assertEquals("f27498caf88eb37360c807743b9fd05b52fcbeeb2c168e9e3af581adf82df8d0",data.sha256());}
    @Test void allThreeSourcesRetainLicenseAndProvenance(){assertEquals(Set.of("CC BY-SA 4.0","ODC-By 1.0","CC0 1.0"),data.products().stream().map(p->p.provenance().license()).collect(java.util.stream.Collectors.toSet()));assertTrue(data.products().stream().allMatch(p->p.provenance().sourceUrl().startsWith("https://www.kaggle.com/")));}
    @Test void datesNotInvented(){assertTrue(data.products().stream().filter(p->!p.store().equals("Flipkart")).allMatch(p->p.observedAt().isBlank()));}
    @Test void myntraMissingRatingsAndMrpRemainUnknown(){assertTrue(data.products().stream().filter(p->p.store().equals("Myntra")).allMatch(p->p.mrp()==null&&p.rating()==null));}
    @Test void sourceSkuIsNotManufacturerModel(){DatasetProduct p=data.products().stream().filter(x->x.store().equals("Myntra")).findFirst().orElseThrow();assertFalse(matcher.models(p).contains(p.productId()));}
    @Test void searchSpansAllThreeStores(){for(String store:List.of("Amazon","Flipkart","Myntra"))assertTrue(service.search("puma","All categories",DatasetService.Sort.LOWEST_PRICE,false,Set.of(),0,12,store).total()>0);}
    @Test void missingSizeDoesNotVerifyFashionVariant(){var a=fixture("Amazon","Puma STYLE123 Black Shoes","Puma","","Black","Men");var b=fixture("Myntra","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.MODEL_MATCH,matcher.assess(a,b).status());}
    @Test void explicitSizeConflictRejected(){var a=fixture("Amazon","Puma STYLE123 Black Shoes","Puma","UK 8","Black","Men");var b=fixture("Myntra","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.CONFLICT,matcher.assess(a,b).status());}
    @Test void sameRecordedModelAndRequiredVariantQualifiesRuleOnly(){var a=fixture("Amazon","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");var b=fixture("Myntra","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.SAME_VARIANT,matcher.assess(a,b).status());}
    @Test void similarGenericTitleIsOnlyCandidate(){var a=fixture("Amazon","Puma Black Running Shoes","Puma","UK 9","Black","Men");var b=fixture("Myntra","Puma Black Running Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.CANDIDATE,matcher.assess(a,b).status());}
    @Test void differentModelCannotWinByCheaperPrice(){var a=fixture("Amazon","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");var b=fixture("Myntra","Puma STYLE999 Black Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.CONFLICT,matcher.assess(a,b).status());}
    @Test void differentColorRejected(){var a=fixture("Amazon","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");var b=fixture("Myntra","Puma STYLE123 Blue Shoes","Puma","UK 9","Blue","Men");assertEquals(CrossStoreMatcher.Status.CONFLICT,matcher.assess(a,b).status());}
    @Test void actualCrossLanguageSharedModelFoundWithoutInventingBrand(){var anchor=data.products().stream().filter(p->p.store().equals("Myntra")&&p.title().contains("NL1805WL01")).findFirst().orElseThrow();var rows=service.crossCandidates(anchor);assertTrue(rows.stream().anyMatch(p->p.store().equals("Amazon")));assertTrue(rows.stream().filter(p->p.store().equals("Amazon")).allMatch(p->matcher.assess(anchor,p).status()==CrossStoreMatcher.Status.MODEL_MATCH));}
    @Test void sameStoreNeverBecomesCrossStoreMatch(){var a=fixture("Amazon","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.UNRELATED,matcher.assess(a,a).status());}
    @Test void boysAndAdultVariantsNotMerged(){var a=fixture("Amazon","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Boys");var b=fixture("Myntra","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.CONFLICT,matcher.assess(a,b).status());}
    @Test void capacityConflictRejected(){var a=fixture("Amazon","Phone MODEL123 128GB Black","Puma","","Black","");var b=fixture("Myntra","Phone MODEL123 256GB Black","Puma","","Black","");assertEquals(CrossStoreMatcher.Status.CONFLICT,matcher.assess(a,b).status());}
    @Test void modelQualifierConflictRejected(){var a=fixture("Amazon","Puma STYLE123 Pro Black Shoes","Puma","UK 9","Black","Men");var b=fixture("Myntra","Puma STYLE123 Black Shoes","Puma","UK 9","Black","Men");assertEquals(CrossStoreMatcher.Status.CONFLICT,matcher.assess(a,b).status());}
}
