package tulita.photocatalog.dao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import tulita.photocatalog.model.PhotoProduct;
import tulita.photocatalog.service.CatalogValidator;
import java.nio.file.*;
import java.util.*;
public final class JsonPhotoCatalogDao implements PhotoCatalogDao{
    private final Path root;
    public JsonPhotoCatalogDao(Path root){this.root=root;}
    /** Data folder: explicit env override, then the working folder, then the installed application folder. */
    public static Path defaultRoot(){
        String override=System.getenv("TULITA_PHOTO_CATALOG_ROOT");
        if(override!=null&&!override.isBlank())return Path.of(override).toAbsolutePath().normalize();
        Path working=Path.of("datasets","promap").toAbsolutePath().normalize();
        if(Files.isDirectory(working))return working;
        for(Path candidate:installRoots()){
            Path root=candidate.resolve("datasets").resolve("promap");
            if(Files.isDirectory(root))return root.toAbsolutePath().normalize();
        }
        return working;
    }
    private static List<Path> installRoots(){
        List<Path> roots=new ArrayList<>();
        try{
            String launcher=System.getProperty("jpackage.app-path");
            if(launcher!=null&&!launcher.isBlank())roots.add(Path.of(launcher).toAbsolutePath().getParent());
        }catch(RuntimeException ignored){}
        try{
            var source=JsonPhotoCatalogDao.class.getProtectionDomain().getCodeSource();
            if(source!=null){
                Path jar=Path.of(source.getLocation().toURI());
                Path base=Files.isDirectory(jar)?jar:jar.getParent();
                // Packaged images put the jar in <app>/app, data in <app>/datasets.
                for(int level=0;level<3&&base!=null;level++){roots.add(base);base=base.getParent();}
            }
        }catch(Exception ignored){}
        return roots;
    }
    public List<PhotoProduct> load()throws Exception{
        var list=new ObjectMapper().readValue(root.resolve("catalog.json").toFile(),new TypeReference<List<PhotoProduct>>(){});
        CatalogValidator.validate(list,root);return List.copyOf(list);
    }
}
