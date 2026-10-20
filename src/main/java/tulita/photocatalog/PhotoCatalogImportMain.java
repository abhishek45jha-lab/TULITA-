package tulita.photocatalog;
import tulita.database.*;
import tulita.photocatalog.dao.*;
public final class PhotoCatalogImportMain{
 public static void main(String[]args)throws Exception{try{var root=JsonPhotoCatalogDao.defaultRoot();var data=new JsonPhotoCatalogDao(root).load();new JdbcPhotoCatalogDao(new ConnectionFactory(AppConfig.load()),root).replace(data);System.out.println("Imported "+data.size()+" curated genuine products, their original USD observations and photo references. Local photos remain required.");}catch(Exception e){System.err.println("Photo catalog import did not complete. Check schema, credentials, permissions and local data assets. No fallback or invented prices.");throw new IllegalStateException("Import unsuccessful; see setup instructions.");}}
}
