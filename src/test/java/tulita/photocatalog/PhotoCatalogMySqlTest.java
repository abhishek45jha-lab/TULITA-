package tulita.photocatalog;
import org.junit.jupiter.api.*;
import tulita.photocatalog.dao.*;
import tulita.photocatalog.model.PhotoProduct;
import tulita.database.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PhotoCatalogMySqlTest{
 @Test void actualMySqlPersistsAllUsdPricesPhotosAndRollback()throws Exception{
  Assumptions.assumeTrue(Boolean.getBoolean("tulita.photoMysqlTests"));var cfg=AppConfig.load();Assumptions.assumeTrue(cfg.url().contains("tulita_review_test"),"Destructive test needs an isolated test database.");var root=JsonPhotoCatalogDao.defaultRoot();var original=new JsonPhotoCatalogDao(root).load();var sql=new JdbcPhotoCatalogDao(new ConnectionFactory(cfg),root);sql.replace(original);var loaded=sql.load();assertEquals(79,loaded.size());assertEquals(161,loaded.stream().mapToInt(p->p.offers().size()).sum());assertEquals(237,loaded.stream().mapToInt(p->p.photos().size()).sum());
  for(var p:original){var actual=loaded.stream().filter(x->x.id().equals(p.id())).findFirst().orElseThrow();assertEquals(p.variant(),actual.variant());for(var o:p.offers()){var a=actual.offers().stream().filter(x->x.store().equals(o.store())).findFirst().orElseThrow();assertEquals(0,o.price().compareTo(a.price()));assertEquals(o.sourceRowSha256(),a.sourceRowSha256());}}
  var p=original.get(0);var oversized=new PhotoProduct("invalid-id-".repeat(20),p.name(),p.brand(),p.model(),p.variant(),p.category(),p.reviewNote(),p.offers(),p.photos());assertThrows(Exception.class,()->sql.replace(List.of(oversized)));assertEquals(79,sql.load().size(),"SQL failure after DELETE must roll back the catalog");
 }
}
