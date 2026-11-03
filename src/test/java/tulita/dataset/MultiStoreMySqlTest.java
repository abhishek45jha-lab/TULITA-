package tulita.dataset;
import org.junit.jupiter.api.*;
import tulita.dataset.dao.*;
import tulita.dataset.model.*;
import tulita.database.*;
import tulita.dao.DataAccessException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MultiStoreMySqlTest {
    @Test void allThreeStoresImportReadAndRollback()throws Exception{
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.multiMysqlTests"));
        var csv=new MultiCsvDao(MultiCsvDao.defaultPath()).load();var dao=new MultiJdbcDao(new ConnectionFactory(AppConfig.load()));dao.replace(csv);var sql=dao.load();assertEquals(52491,sql.products().size());assertEquals(csv.sha256(),sql.sha256());
        Map<String,DatasetProduct> expected=new HashMap<>();csv.products().forEach(p->expected.put(p.recordId(),p));
        for(var p:sql.products()){var a=expected.get(p.recordId());assertNotNull(a);assertEquals(a.title(),p.title());assertEquals(a.provenance(),p.provenance());if(a.price()==null)assertNull(p.price());else assertEquals(0,a.price().compareTo(p.price()));}
        var p=csv.products().get(0);var bad=new DatasetSnapshot(List.of(p,p),csv.sourceTitle(),csv.sourceUrl(),csv.license(),csv.sha256(),csv.firstObserved(),csv.lastObserved(),"CSV");assertThrows(DataAccessException.class,()->dao.replace(bad));assertEquals(52491,dao.load().products().size());
    }
}
