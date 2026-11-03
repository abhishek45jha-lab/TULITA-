package tulita.photocatalog.service;
import tulita.photocatalog.model.PhotoProduct;
import tulita.matcher.ProductMatcher;
import java.util.*;
public final class CatalogService{
    private final List<PhotoProduct> products;private final Map<String,String> text=new HashMap<>();private final ProductMatcher normalizer=new ProductMatcher();
    public CatalogService(List<PhotoProduct> data){products=List.copyOf(data);for(var p:data)text.put(p.id(),normalizer.normalize(p.name()+" "+p.brand()+" "+p.model()+" "+p.variant()+" "+p.offers().stream().map(PhotoProduct.Offer::title).reduce("",(a,b)->a+" "+b)));}
    public List<PhotoProduct> products(){return products;}
    public List<PhotoProduct> search(String query,String category,String store,boolean three,boolean savedOnly,Set<String> saved,int sort){String[] terms=normalizer.normalize(query).split(" ");var stream=products.stream().filter(p->Arrays.stream(terms).allMatch(text.get(p.id())::contains)).filter(p->category.equals("All categories")||p.category().equals(category)).filter(p->store.equals("All stores")||p.offers().stream().anyMatch(o->o.store().equals(store))).filter(p->!three||p.offers().size()>=3).filter(p->!savedOnly||saved.contains(p.id()));Comparator<PhotoProduct> comparator=sort==1?Comparator.comparing(PhotoProduct::lowest):sort==2?Comparator.<PhotoProduct>comparingInt(p->p.offers().size()).reversed():Comparator.comparing(PhotoProduct::name);return stream.sorted(comparator.thenComparing(PhotoProduct::id)).toList();}
    public static java.math.BigDecimal highest(PhotoProduct p){return p.offers().stream().map(PhotoProduct.Offer::price).max(java.math.BigDecimal::compareTo).orElseThrow();}
    public static java.math.BigDecimal spread(PhotoProduct p){return highest(p).subtract(p.lowest());}
    public static int savingsPercent(PhotoProduct p){var high=highest(p);if(high.signum()==0)return 0;return spread(p).multiply(java.math.BigDecimal.valueOf(100)).divide(high,0,java.math.RoundingMode.HALF_UP).intValue();}
    public static java.math.BigDecimal priceAt(PhotoProduct p,String store){return p.offers().stream().filter(o->o.store().equals(store)).map(PhotoProduct.Offer::price).findFirst().orElse(null);}
    public static String money(java.math.BigDecimal value,String currency){return currency+" "+value.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
}
