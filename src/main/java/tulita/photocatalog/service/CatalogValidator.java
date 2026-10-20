package tulita.photocatalog.service;
import tulita.photocatalog.model.PhotoProduct;
import org.apache.commons.csv.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.math.BigDecimal;
/** Validate curated memberships against pinned original CSV rows and cached photo fingerprints. */
public final class CatalogValidator {
    public static final List<String> STORES=List.of("Amazon","Walmart","Target","Sears");
    public static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    public static Path safe(Path root,String relative){Path base=root.toAbsolutePath().normalize();Path path=base.resolve(relative).normalize();if(Path.of(relative).isAbsolute()||!path.startsWith(base))throw new IllegalArgumentException("Catalog asset path escapes its data folder.");return path;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    public static void validate(List<PhotoProduct> products,Path root)throws Exception{
        require(!products.isEmpty(),"Photo catalog is empty. No legacy/demo fallback is used.");
        ObjectMapper json=new ObjectMapper();JsonNode manifest=json.readTree(root.resolve("MANIFEST.json").toFile());
        Map<String,List<Map<String,String>>> rows=new HashMap<>();
        for(var entries=manifest.path("source_files_sha256").fields();entries.hasNext();){var entry=entries.next();Path file=safe(root,"raw/"+entry.getKey());require(sha(Files.readAllBytes(file)).equals(entry.getValue().asText()),"Original ProMap source file changed: "+entry.getKey());
            try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8);var csv=CSVParser.parse(reader,CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())){List<Map<String,String>> list=new ArrayList<>();for(var row:csv)list.add(new LinkedHashMap<>(row.toMap()));rows.put(entry.getKey(),list);}
        }
        Set<String> ids=new HashSet<>(),used=new HashSet<>();
        for(var p:products){require(ids.add(p.id())&&!p.id().isBlank()&&!p.name().isBlank()&&!p.variant().isBlank()&&p.reviewNote().length()>=30,"Invalid/duplicate reviewed canonical identity.");require(p.offers().size()>=2,"Every displayed product needs at least two reviewed, priced observations.");Set<String> stores=new HashSet<>();Set<String> imageUrls=new HashSet<>();
            for(var o:p.offers()){
                require(STORES.contains(o.store())&&stores.add(o.store()),"Duplicate/unsupported store for variant.");require(used.add(o.url()),"Source observation reused across canonical products.");
                require(o.price()!=null&&o.price().signum()>=0&&o.currency().equals("USD"),"Invalid recorded amount/currency; no conversion is allowed.");
                var source=rows.get(o.sourceFile());require(source!=null&&o.sourceRow()>=2&&o.sourceRow()-2<source.size()&&(o.sourceSide()==1||o.sourceSide()==2),"Missing source evidence.");var r=source.get(o.sourceRow()-2);String side=String.valueOf(o.sourceSide());
                StringBuilder encoded=new StringBuilder();for(String value:r.values())encoded.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
                require(sha(encoded.toString().getBytes(StandardCharsets.UTF_8)).equals(o.sourceRowSha256()),"Source row fingerprint changed.");
                String host=java.net.URI.create(o.url()).getHost();String domain=o.store().toLowerCase(Locale.ROOT)+".com";
                require(host!=null&&(host.equals(domain)||host.equals("www."+domain)),"Retailer name does not match source URL.");
                require(r.get("match").equals("1")&&r.get("id"+side).equals(o.url())&&r.get("name"+side).equals(o.title())&&new BigDecimal(r.get("price"+side)).compareTo(o.price())==0&&r.get("specification"+side).equals(o.specifications())&&r.getOrDefault("long_description"+side,"").equals(o.description()),"Reviewed offer differs from its original source observation.");
                require(o.observedAt().isEmpty(),"This source supplies no per-record capture date; do not invent one.");
                if(!p.model().equals("Not provided"))for(var spec:json.readTree(o.specifications())){
                    String key=spec.path("key").asText().strip().toLowerCase(Locale.ROOT);String value=spec.path("value").asText();
                    if(Set.of("model","model info","item model number").contains(key)&&!value.isBlank())require(value.replaceAll("[^A-Za-z0-9]","").equalsIgnoreCase(p.model().replaceAll("[^A-Za-z0-9]","")),"Known source model conflicts with the canonical declaration.");
                }
                for(var url:json.readTree(r.get("image_url"+side)))imageUrls.add(o.store()+"::"+url.asText());
            }
            require(!p.photos().isEmpty(),"A photo-search product needs reference photos.");
            for(var photo:p.photos())require(stores.contains(photo.store())&&imageUrls.contains(photo.store()+"::"+photo.sourceUrl())&&sha(Files.readAllBytes(safe(root,photo.path()))).equals(photo.sha256()),"Missing, changed or unrelated catalog photograph.");
        }
    }
}
