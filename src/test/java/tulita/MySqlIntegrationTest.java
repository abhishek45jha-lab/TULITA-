package tulita;
import org.junit.jupiter.api.*;
import tulita.dao.*;
import tulita.database.*;
import tulita.service.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in after importing schema.sql: mvn test -Dtulita.mysqlTests=true */
class MySqlIntegrationTest {
    @Test void mysqlSnapshotMatchesBundledSeed() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("tulita.mysqlTests"));
        AppConfig c=AppConfig.load();
        CatalogService sql=new CatalogService(new JdbcCatalogDao(new ConnectionFactory(c)).load());
        CatalogService demo=new CatalogService(new DemoCatalogDao().load());
        assertEquals(20,sql.productCount()); assertEquals(54,sql.listingCount()); assertEquals(0,sql.excludedCount());
        var sqlProducts=sql.search("","All products",CatalogService.Sort.LOWEST_PRICE,false,new WatchlistService());
        var demoProducts=demo.search("","All products",CatalogService.Sort.LOWEST_PRICE,false,new WatchlistService());
        for(int i=0;i<sqlProducts.size();i++) {
            assertEquals(demoProducts.get(i).getId(),sqlProducts.get(i).getId());
            assertEquals(0,demo.best(demoProducts.get(i)).orElseThrow().finalPrice().orElseThrow().compareTo(sql.best(sqlProducts.get(i)).orElseThrow().finalPrice().orElseThrow()));
        }
    }
}
