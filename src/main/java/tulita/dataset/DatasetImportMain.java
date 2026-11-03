package tulita.dataset;
import tulita.dataset.dao.*;
import tulita.database.*;
import java.nio.file.Path;

/** Run from project root using Maven exec:java. Never prints credentials or vendor exceptions. */
public final class DatasetImportMain {
    private DatasetImportMain() { }
    public static void main(String[] args) {
        try {
            DatasetConfig config=DatasetConfig.load();
            Path file=args.length>0 ? Path.of(args[0]) : config.csvPath();
            var snapshot=new CsvDatasetDao(file).load();
            new JdbcDatasetDao(new ConnectionFactory(AppConfig.load())).replace(snapshot);
            System.out.println("Imported "+snapshot.products().size()+" HISTORICAL Flipkart records into MySQL.");
            System.out.println("Source: "+snapshot.sourceUrl());
            System.out.println("Observed: "+snapshot.firstObserved()+" to "+snapshot.lastObserved());
            System.out.println("SHA-256: "+snapshot.sha256());
            System.out.println("Not live data. Existing demo tables were not changed.");
        } catch(Exception e) {
            System.err.println(e instanceof tulita.dao.DataAccessException ? e.getMessage() : "Import configuration is invalid. Check application.properties and the dataset path.");
            // Propagate a sanitized failure so Maven exits nonzero without exposing DB credentials.
            throw new IllegalStateException("Dataset import did not complete. See the setup message above.");
        }
    }
}
