package tulita.dataset.dao;

import org.apache.commons.csv.*;
import tulita.dao.DataAccessException;
import tulita.dataset.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.security.*;
import java.util.*;

/** CSV parser handles quotes, commas, escaped quotes and multiline source fields. No scraping. */
public final class CsvDatasetDao implements DatasetDao {
    private final Path path;
    public CsvDatasetDao(Path path) { this.path=path; }
    @Override public DatasetSnapshot load() throws DataAccessException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=Files.newInputStream(path);
                DigestInputStream hashed=new DigestInputStream(input,digest);
                Reader reader=new InputStreamReader(hashed,StandardCharsets.UTF_8);
                CSVParser csv=CSVParser.parse(reader,CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build())) {
                for(String field:List.of("uniq_id","crawl_timestamp","product_url","product_name","product_category_tree","pid","retail_price","discounted_price","product_rating","brand","description","product_specifications"))
                    if(!csv.getHeaderMap().containsKey(field)) throw new IOException("Required CSV column missing: "+field);
                List<DatasetProduct> items=new ArrayList<>(); Set<String> ids=new HashSet<>();
                String first="", last="";
                for(CSVRecord row:csv) {
                    if(Thread.currentThread().isInterrupted()) throw new IOException("Dataset load cancelled.");
                    String id=row.get("uniq_id").trim(), title=row.get("product_name").trim();
                    if(id.isBlank() || title.isBlank() || !ids.add(id)) throw new IOException("Missing/duplicate record ID or missing title at record "+row.getRecordNumber());
                    String observed=row.get("crawl_timestamp").trim();
                    if(!observed.isBlank()) { if(first.isEmpty() || observed.compareTo(first)<0) first=observed; if(last.isEmpty() || observed.compareTo(last)>0) last=observed; }
                    String path=row.get("product_category_tree");
                    items.add(new DatasetProduct(id,row.get("pid"),title,category(path),path,row.get("brand"),
                        amount(row.get("discounted_price"),false),amount(row.get("retail_price"),false),amount(row.get("product_rating"),true),
                        observed,row.get("product_url"),row.get("description"),row.get("product_specifications")));
                }
                if(items.isEmpty()) throw new IOException("Dataset has no records.");
                return new DatasetSnapshot(items,DatasetSnapshot.TITLE,DatasetSnapshot.URL,DatasetSnapshot.LICENSE,
                    HexFormat.of().formatHex(digest.digest()),first,last,"CSV");
            }
        } catch(IOException | NoSuchAlgorithmException | RuntimeException e) {
            throw new DataAccessException("Could not read the Flipkart CSV. Keep datasets/flipkart_com-ecommerce_sample.csv in the project, or check TULITA_DATASET_PATH. File must have the original PromptCloud columns. No demo fallback was used.",e);
        }
    }
    static BigDecimal amount(String raw, boolean rating) {
        try {
            BigDecimal n=new BigDecimal(raw.trim());
            return n.signum()<0 || (rating && n.compareTo(new BigDecimal("5"))>0) ? null : n;
        } catch(RuntimeException e) { return null; }
    }
    static String category(String raw) {
        String text=raw.trim().replaceFirst("^\\[\\s*\"?", "");
        int split=text.indexOf(">>"); if(split>=0) text=text.substring(0,split);
        return text.replaceAll("[\"\\]]+$", "").trim().isBlank() ? "Uncategorised" : text.replaceAll("[\"\\]]+$", "").trim();
    }
}
