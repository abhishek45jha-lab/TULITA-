package tulita.dataset;
import org.junit.jupiter.api.*;
import tulita.dataset.dao.*;
import tulita.dataset.model.*;
import tulita.database.*;
import tulita.dao.DataAccessException;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in: mvn test -Dtulita.datasetMysqlTests=true . Requires dataset-schema.sql.
 * This intentionally imports/replaces only the named historical source. */
class DatasetMySqlTest {
    @Test void fullImportRoundTripAndRollback() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.datasetMysqlTests"));
        var csv=new CsvDatasetDao(Path.of("datasets/flipkart_com-ecommerce_sample.csv")).load();
        var dao=new JdbcDatasetDao(new ConnectionFactory(AppConfig.load())); dao.replace(csv);
        var sql=dao.load(); assertEquals(20000,sql.products().size()); assertEquals(csv.sha256(),sql.sha256());
        Map<String,DatasetProduct> expected=new HashMap<>(); csv.products().forEach(p->expected.put(p.recordId(),p));
        for(var p:sql.products()) {
            var original=expected.get(p.recordId()); assertNotNull(original); assertEquals(original.title(),p.title()); assertEquals(original.description(),p.description()); assertEquals(original.observedAt(),p.observedAt());
            if(original.price()==null)assertNull(p.price());else assertEquals(0,original.price().compareTo(p.price()));
            if(original.rating()==null)assertNull(p.rating());else assertEquals(0,original.rating().compareTo(p.rating()));
        }
        DatasetProduct first=csv.products().get(0);
        var invalid=new DatasetSnapshot(List.of(first,first),csv.sourceTitle(),csv.sourceUrl(),csv.license(),csv.sha256(),csv.firstObserved(),csv.lastObserved(),"CSV");
        assertThrows(DataAccessException.class,()->dao.replace(invalid)); assertEquals(20000,dao.load().products().size());
    }
}
