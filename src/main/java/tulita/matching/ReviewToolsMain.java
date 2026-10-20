package tulita.matching;
import tulita.matching.dao.*;
import tulita.matching.service.*;
import tulita.dataset.*;
import tulita.dataset.dao.*;
import tulita.dataset.model.*;
import tulita.database.*;
import org.apache.commons.csv.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

/** Data inspection/review tools, not an automatic match generator. */
public final class ReviewToolsMain {
    public static void main(String[]args)throws Exception{
        try{
            if(args.length>2||(args.length>0&&!java.util.Set.of("--export","--validate","--import-mysql").contains(args[0])))throw new IllegalArgumentException("Use --export [file], --validate [mapping.csv], or --import-mysql [mapping.csv].");
            var config=DatasetConfig.load();var factory=new ConnectionFactory(AppConfig.load());
            DatasetSnapshot catalog=(config.storage().equals("mysql")||(args.length>0&&args[0].equals("--import-mysql")))?new MultiJdbcDao(factory).load():new MultiCsvDao(MultiCsvDao.defaultPath()).load();
            if(args.length>0&&args[0].equals("--export")){
                Path file=Path.of(args.length>1?args[1]:"matching/record-index.csv");export(catalog,file);System.out.println("Exported "+catalog.products().size()+" source records and fingerprints. No matches were generated: "+file);return;
            }
            Path file=args.length>1?Path.of(args[1]):ReviewedCsvDao.defaultPath();var links=new ReviewedCsvDao().load(file);var service=new SameProductService(catalog,links);
            if(args.length>0&&args[0].equals("--import-mysql")){new ReviewedJdbcDao(factory).replace(links);System.out.println("Imported reviewed identity links. No source prices were changed.");}
            else System.out.println("Validated "+links.size()+" reviewed links, "+service.groups().size()+" canonical variants, "+service.threeStoreCount()+" covering all three stores. No similarity fallback.");
        }catch(Exception ex){System.err.println(ex instanceof tulita.dao.DataAccessException||ex instanceof IllegalArgumentException||ex instanceof IOException?ex.getMessage():"Review-tool configuration is invalid.");throw new IllegalStateException("Review operation did not complete. Source prices were not edited.");}
    }
    public static void export(DatasetSnapshot data,Path file)throws IOException{
        Path parent=file.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        try(Writer writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8);CSVPrinter csv=new CSVPrinter(writer,CSVFormat.DEFAULT)){
            csv.printRecord("record_id","store","title","brand","price_inr","source_size","source_color","source_gender","observed_at","source_url","record_sha256");
            for(var p:data.products())csv.printRecord(p.recordId(),p.store(),safe(p.title()),safe(p.brand()),p.price()==null?"":p.price().toPlainString(),safe(p.provenance().size()),safe(p.provenance().color()),safe(p.provenance().gender()),p.observedAt(),p.provenance().sourceUrl(),RecordFingerprint.of(p));
        }
    }
    private static String safe(String text){return !text.stripLeading().isEmpty()&&"=+-@".indexOf(text.stripLeading().charAt(0))>=0?"'"+text:text;}
}
