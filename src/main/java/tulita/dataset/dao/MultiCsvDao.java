package tulita.dataset.dao;
import tulita.dataset.model.*;
import tulita.dao.DataAccessException;
import org.apache.commons.csv.*;
import java.util.zip.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;

/** Reads the bundled normalized historical catalog directly from ZIP, without extraction. */
public final class MultiCsvDao implements DatasetDao {
    private final Path path;
    public MultiCsvDao(Path path){this.path=path;}
    @Override public DatasetSnapshot load()throws DataAccessException{
        try(ZipFile zip=new ZipFile(path.toFile())){
            ZipEntry entry=zip.getEntry("catalog.csv");if(entry==null)throw new IOException("Missing catalog.csv in ZIP");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream stream=zip.getInputStream(entry);DigestInputStream hashed=new DigestInputStream(stream,digest);
                Reader reader=new InputStreamReader(hashed,StandardCharsets.UTF_8);
                CSVParser parser=CSVParser.parse(reader,CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())){
                Set<String> required=Set.of("store","source_key","source_url","license","date_note","source_record_id","product_id","title","category","category_path","brand","price","mrp","rating","observed_at","url","availability","size","color","gender","quantity","description","specifications");
                if(!parser.getHeaderMap().keySet().containsAll(required))throw new IOException("Unsupported catalog columns");
                List<DatasetProduct> rows=new ArrayList<>();Set<String> ids=new HashSet<>();
                for(CSVRecord r:parser){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
                    String store=r.get("store");if(!Set.of("Amazon","Flipkart","Myntra").contains(store))throw new IOException("Unsupported store");
                    String id=store+"-"+r.get("source_record_id");if(!ids.add(id))throw new IOException("Duplicate source ID");
                    RecordProvenance meta=new RecordProvenance(store,r.get("source_key"),r.get("source_url"),r.get("license"),r.get("date_note"),r.get("availability"),r.get("size"),r.get("color"),r.get("gender"),r.get("quantity"));
                    rows.add(new DatasetProduct(id,r.get("product_id"),r.get("title"),r.get("category"),r.get("category_path"),r.get("brand"),number(r.get("price"),false),number(r.get("mrp"),false),number(r.get("rating"),true),r.get("observed_at"),r.get("url"),r.get("description"),r.get("specifications"),meta));
                }
                if(rows.isEmpty())throw new IOException("Empty catalog");
                return new DatasetSnapshot(rows,"Amazon + Flipkart + Myntra", "See datasets/multistore/ATTRIBUTION.md","Per-source CC BY-SA / ODC-By / CC0",HexFormat.of().formatHex(digest.digest()),"2015–2016 Flipkart","2021 Myntra / 2023 Amazon","CSV");
            }
        }catch(IOException|NoSuchAlgorithmException|RuntimeException ex){throw new DataAccessException("Could not read datasets/multistore/catalog.csv.zip. Keep the full datasets folder or check TULITA_MULTI_DATASET_PATH. No demo fallback was used.",ex);}
    }
    private BigDecimal number(String raw,boolean rating){try{BigDecimal n=new BigDecimal(raw);return n.signum()<0||(rating&&n.compareTo(new BigDecimal("5"))>0)?null:n;}catch(RuntimeException ex){return null;}}
    public static Path defaultPath(){return Path.of(System.getenv().getOrDefault("TULITA_MULTI_DATASET_PATH","datasets/multistore/catalog.csv.zip"));}
}
