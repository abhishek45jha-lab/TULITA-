package tulita.dataset;
import tulita.dataset.dao.*;
import tulita.database.*;
public final class MultiImportMain {
    private MultiImportMain(){}
    public static void main(String[]args){try{var snapshot=new MultiCsvDao(MultiCsvDao.defaultPath()).load();new MultiJdbcDao(new ConnectionFactory(AppConfig.load())).replace(snapshot);System.out.println("Imported "+snapshot.products().size()+" historical Amazon/Flipkart/Myntra records into MySQL.");System.out.println("No live prices or fictional offers imported. Source checksum: "+snapshot.sha256());}catch(Exception ex){System.err.println(ex instanceof tulita.dao.DataAccessException?ex.getMessage():"Invalid local configuration.");throw new IllegalStateException("Multi-store import did not complete. See setup message above.");}}
}
