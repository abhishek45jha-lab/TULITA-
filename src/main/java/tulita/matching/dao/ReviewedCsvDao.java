package tulita.matching.dao;
import tulita.matching.model.*;
import org.apache.commons.csv.*;
import java.util.*;
import java.time.LocalDate;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class ReviewedCsvDao {
    public static final String[] HEADERS={"canonical_id","canonical_name","brand","model","category","variant_key","size","color","capacity","quantity","gender","store","record_id","record_sha256","evidence_url","reviewed_on","review_note"};
    public static Path defaultPath(){return Path.of(System.getenv().getOrDefault("TULITA_MATCHES_PATH","matching/same-product-matches.csv"));}
    public List<ReviewedLink> load(Path path)throws IOException{
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            reader.mark(1);if(reader.read()!=0xfeff)reader.reset();
            try(CSVParser csv=CSVParser.parse(reader,CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setAllowDuplicateHeaderNames(false).build())){
            if(!csv.getHeaderMap().keySet().equals(new LinkedHashSet<>(Arrays.asList(HEADERS))))throw new IOException("Mapping CSV has missing columns.");
            List<ReviewedLink> result=new ArrayList<>();
            for(var r:csv){var c=new CanonicalProduct(r.get("canonical_id"),r.get("canonical_name"),r.get("brand"),r.get("model"),r.get("category"),r.get("variant_key"),r.get("size"),r.get("color"),r.get("capacity"),r.get("quantity"),r.get("gender"));result.add(new ReviewedLink(c,r.get("store"),r.get("record_id"),r.get("record_sha256"),r.get("evidence_url"),LocalDate.parse(r.get("reviewed_on")),r.get("review_note")));}return List.copyOf(result);}
        }catch(RuntimeException e){throw new IOException("Mapping CSV contains an invalid date or row. No mappings were accepted.",e);}
    }
}
