package tulita.matching;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tulita.matching.model.*;
import tulita.matching.service.*;
import tulita.matching.dao.*;
import tulita.dataset.dao.*;
import tulita.dataset.service.DatasetService;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static tulita.matching.ReviewFixtures.*;
class SameProductTest {
    @TempDir Path temp;
    List<tulita.dataset.model.DatasetProduct> rows(){return SameProductService.STORES.stream().map(ReviewFixtures::row).toList();}
    SameProductService service(){var rows=rows();return new SameProductService(snapshot(rows),rows.stream().map(ReviewFixtures::link).toList());}
    @Test void exactVariantHasOneCardAndOriginalSourceAmounts(){var s=service();assertEquals(1,s.threeStoreCount());var g=s.groups().get(0);assertEquals(3,g.listings().size());assertEquals("900",g.listings().get("Amazon").price().toPlainString());assertEquals("1000",g.listings().get("Flipkart").price().toPlainString());assertEquals(1,s.search("TEST900","All categories",DatasetService.Sort.LOWEST_PRICE,false,Set.of(),0,12,"All stores",true).total());}
    @Test void noReviewDoesNotAutoCompareIdenticalModelTitles(){var s=new SameProductService(snapshot(rows()),List.of());assertEquals(0,s.threeStoreCount());assertEquals(0,s.search("","All categories",DatasetService.Sort.LOWEST_PRICE,false,Set.of(),0,12,"All stores",true).total());assertEquals(3,s.search("","All categories",DatasetService.Sort.LOWEST_PRICE,false,Set.of(),0,12,"All stores",false).total());assertTrue(s.groupFor("Amazon-test").isEmpty());}
    @Test void missingThirdStoreIsNotSubstituted(){var r=rows();var s=new SameProductService(snapshot(r),r.subList(0,2).stream().map(ReviewFixtures::link).toList());assertFalse(s.groups().get(0).allThree());assertNull(s.groups().get(0).listings().get("Myntra"));assertEquals(0,s.threeStoreCount());}
    @Test void rejectsSourceReuse(){var p=row("Amazon");assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(link(p),link(p))));}
    @Test void rejectsTwoRecordsInOneStore(){var p=row("Amazon");var q=new tulita.dataset.model.DatasetProduct("Amazon-other",p.productId(),p.title(),p.category(),p.categoryPath(),p.brand(),p.price(),p.mrp(),p.rating(),p.observedAt(),p.url(),p.description(),p.specifications(),p.provenance());assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p,q)),List.of(link(p),link(q))));}
    @Test void wrongModelCannotBecomeMembership(){var p=row("Amazon");assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(link(p,changed(canonical(),"TEST999","Black","")))));}
    @Test void wrongColorRejectedEvenForSingleStore(){var p=row("Amazon");assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(link(p,changed(canonical(),"TEST900","Blue","")))));}
    @Test void capacityTitleCannotBeOverridden(){var p=title(row("Amazon"),"TEST FIXTURE ONLY Demo TEST900 Black Men Watch 128GB");assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(link(p,changed(canonical(),"TEST900","Black","256GB")))));}
    @Test void explicitTitlePackCannotBeOverridden(){var p=title(row("Amazon"),"TEST FIXTURE ONLY Demo TEST900 Black Men Watch pack of 2");assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(link(p))));}
    @Test void titleSizeSystemCannotBeOverridden(){var p=title(row("Amazon"),"TEST FIXTURE ONLY Demo TEST900 Black Men Watch size US 9");var c=canonical();var wrong=new CanonicalProduct(c.id(),c.name(),c.brand(),c.model(),c.category(),c.variantKey(),"UK 9",c.color(),c.capacity(),c.quantity(),c.gender());assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(link(p,wrong))));}
    @Test void staleFingerprintFailsAtomically(){var p=row("Amazon");var bad=new ReviewedLink(canonical(),p.store(),p.recordId(),"0".repeat(64),"https://example.invalid",java.time.LocalDate.now(),"TEST FIXTURE ONLY review note");assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(bad)));}
    @Test void conflictingCanonicalDefinitionsFail(){var r=rows();var c=canonical();var other=new CanonicalProduct(c.id(),"Different canonical name",c.brand(),c.model(),c.category(),c.variantKey(),c.size(),c.color(),c.capacity(),c.quantity(),c.gender());assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(r),List.of(link(r.get(0)),link(r.get(1),other))));}
    @Test void unknownRecordAndWrongStoreFail(){var p=row("Amazon");assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of()),List.of(link(p))));var l=link(p);assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(new ReviewedLink(l.product(),"Myntra",l.recordId(),l.recordSha256(),l.evidenceUrl(),l.reviewedOn(),l.reviewNote()))));}
    @Test void insecureEvidenceRejected(){var p=row("Amazon");var l=link(p);assertThrows(IllegalArgumentException.class,()->new SameProductService(snapshot(List.of(p)),List.of(new ReviewedLink(l.product(),l.store(),l.recordId(),l.recordSha256(),"http://example.invalid",l.reviewedOn(),l.reviewNote()))));}
    @Test void groupWatchAndPagination(){var s=service();assertEquals(1,s.search("","All categories",DatasetService.Sort.LOWEST_PRICE,true,Set.of("Myntra-test"),4,12,"All stores",true).total());assertEquals(3,s.recordIdsFor("Amazon-test").size());assertThrows(IllegalArgumentException.class,()->s.search("","All categories",DatasetService.Sort.LOWEST_PRICE,false,Set.of(),0,0,"All stores",true));}
    @Test void bundledGenuineCatalogStartsWithoutInventedLinks()throws Exception{var data=new MultiCsvDao(MultiCsvDao.defaultPath()).load();var links=new ReviewedCsvDao().load(Path.of("matching/same-product-matches.csv"));assertEquals(52491,data.products().size());assertTrue(links.isEmpty());assertEquals(0,new SameProductService(data,links).threeStoreCount());}
    @Test void headerOnlyAndUtf8BomAccepted()throws Exception{Path file=temp.resolve("review.csv");Files.writeString(file,"\ufeff"+String.join(",",ReviewedCsvDao.HEADERS)+"\n");assertTrue(new ReviewedCsvDao().load(file).isEmpty());}
    @Test void badCsvRejected()throws Exception{Path file=temp.resolve("bad.csv");Files.writeString(file,"id,store\nabc,Amazon\n");assertThrows(java.io.IOException.class,()->new ReviewedCsvDao().load(file));}
    @Test void exportBindsExactOriginalRecord()throws Exception{Path file=temp.resolve("ids.csv");var p=row("Amazon");ReviewToolsMain.export(snapshot(List.of(p)),file);String text=Files.readString(file);assertTrue(text.contains(RecordFingerprint.of(p)));assertTrue(text.contains(p.recordId()));}
}
