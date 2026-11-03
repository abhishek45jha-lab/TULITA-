package tulita.matching;
import org.junit.jupiter.api.*;
import tulita.database.*;
import tulita.dataset.dao.*;
import tulita.matching.dao.*;
import tulita.matching.model.*;
import tulita.matching.service.*;
import tulita.dao.DataAccessException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static tulita.matching.ReviewFixtures.*;
class ReviewedMySqlTest {
    @Test void realMySqlMappingRoundTripInvalidReviewAndTransactionalRollback()throws Exception{
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.reviewMysqlTests"));
        var config=AppConfig.load();Assumptions.assumeTrue(config.url().contains("tulita_review_test"),"Destructive fixture test only runs in dedicated tulita_review_test database.");
        var factory=new ConnectionFactory(config);var source=new MultiJdbcDao(factory);var review=new ReviewedJdbcDao(factory);
        try{
            var rows=SameProductService.STORES.stream().map(ReviewFixtures::row).toList();source.replace(snapshot(rows));
            var links=rows.stream().map(ReviewFixtures::link).toList();review.replace(links);var actual=source.load();assertEquals(3,review.load().size());assertEquals(1,new SameProductService(actual,review.load()).threeStoreCount());
            for(var p:actual.products())assertEquals(RecordFingerprint.of(row(p.store())),RecordFingerprint.of(p),"SQL decimal scale must not break fingerprints");
            assertThrows(DataAccessException.class,()->review.replace(List.of()));assertEquals(3,review.load().size());
            var c=canonical();var colliding=new CanonicalProduct(c.id().toUpperCase(Locale.ROOT),c.name(),c.brand(),c.model(),c.category(),c.variantKey(),c.size(),c.color(),c.capacity(),c.quantity(),c.gender());
            assertThrows(DataAccessException.class,()->review.replace(List.of(links.get(0),link(rows.get(1),colliding),links.get(2))));
            assertEquals(3,review.load().size(),"SQL failure after DELETE must roll back existing identity links");
            try(var conn=factory.open();var ps=conn.prepareStatement("UPDATE multi_records SET brand='Changed source' WHERE record_id='Amazon-test'")){ps.executeUpdate();}
            assertThrows(IllegalArgumentException.class,()->new SameProductService(source.load(),review.load()));assertThrows(DataAccessException.class,()->review.replace(links));assertEquals(3,review.load().size());
            assertEquals(0,row("Amazon").price().compareTo(source.load().products().stream().filter(p->p.store().equals("Amazon")).findFirst().orElseThrow().price()));
        }finally{source.replace(new MultiCsvDao(MultiCsvDao.defaultPath()).load());}
        assertEquals(0,review.load().size(),"Source replacement cascades away obsolete identity links; never silently reuses them");
    }
}
