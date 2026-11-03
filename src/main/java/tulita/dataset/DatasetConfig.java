package tulita.dataset;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public record DatasetConfig(String storage, Path csvPath) {
    public static DatasetConfig load() throws IOException {
        Properties p=new Properties(); Path path=Path.of("application.properties");
        if(Files.exists(path)) try(InputStream in=Files.newInputStream(path)) { p.load(in); }
        String storage=System.getenv().getOrDefault("TULITA_DATA_SOURCE",p.getProperty("dataset.storage","csv")).toLowerCase(Locale.ROOT);
        if(!Set.of("csv","mysql").contains(storage)) throw new IOException("dataset.storage must be csv or mysql.");
        String file=System.getenv().getOrDefault("TULITA_DATASET_PATH",p.getProperty("dataset.csv","datasets/flipkart_com-ecommerce_sample.csv"));
        return new DatasetConfig(storage,Path.of(file));
    }
}
