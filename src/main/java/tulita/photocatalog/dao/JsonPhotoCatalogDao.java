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
    public static Path defaultRoot(){return Path.of(System.getenv().getOrDefault("TULITA_PHOTO_CATALOG_ROOT","datasets/promap")).toAbsolutePath().normalize();}
    public List<PhotoProduct> load()throws Exception{
        var list=new ObjectMapper().readValue(root.resolve("catalog.json").toFile(),new TypeReference<List<PhotoProduct>>(){});
        CatalogValidator.validate(list,root);return List.copyOf(list);
    }
}
