package tulita.dataset.service;

import tulita.dataset.model.*;
import tulita.matcher.ProductMatcher;
import java.util.*;

/** Immutable index; only one page of cards is constructed by the view. No title-based merges. */
public final class DatasetService {
    public enum Sort { LOWEST_PRICE, HIGHEST_DISCOUNT, TOP_RATED }
    public record Page(List<DatasetProduct> items,int page,int pages,int total) { public Page { items=List.copyOf(items); } }
    private final DatasetSnapshot snapshot;
    private final Map<String,List<DatasetProduct>> brandIndex=new HashMap<>(),modelIndex=new HashMap<>();
    private final CrossStoreMatcher crossMatcher=new CrossStoreMatcher();
    private final Map<String,String> searchable;
    private final Map<Sort,List<DatasetProduct>> orders;
    private final ProductMatcher matcher=new ProductMatcher();
    public DatasetService(DatasetSnapshot snapshot) {
        this.snapshot=snapshot;
        for(DatasetProduct p:snapshot.products()){
            String brand=matcher.normalize(p.brand());if(!brand.isBlank())brandIndex.computeIfAbsent(brand,k->new ArrayList<>()).add(p);
            for(String code:crossMatcher.models(p))modelIndex.computeIfAbsent(code,k->new ArrayList<>()).add(p);
        }
        Map<String,String> index=new HashMap<>();
        snapshot.products().forEach(p -> index.put(p.recordId(),matcher.normalize(p.title()+" "+p.brand()+" "+p.categoryPath()+" "+p.productId())));
        searchable=Map.copyOf(index);
        Comparator<DatasetProduct> price=Comparator.comparing(DatasetProduct::price,Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<DatasetProduct> discount=Comparator.comparing(p -> p.discount().orElse(null),Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<DatasetProduct> rating=Comparator.comparing(DatasetProduct::rating,Comparator.nullsLast(Comparator.reverseOrder()));
        Map<Sort,List<DatasetProduct>> sorted=new EnumMap<>(Sort.class);
        sorted.put(Sort.LOWEST_PRICE,sort(snapshot.products(),price)); sorted.put(Sort.HIGHEST_DISCOUNT,sort(snapshot.products(),discount)); sorted.put(Sort.TOP_RATED,sort(snapshot.products(),rating));
        orders=Map.copyOf(sorted);
    }
    private List<DatasetProduct> sort(List<DatasetProduct> products,Comparator<DatasetProduct> comparator) { return products.stream().sorted(comparator.thenComparing(DatasetProduct::title).thenComparing(DatasetProduct::recordId)).toList(); }
    public List<DatasetProduct> crossCandidates(DatasetProduct anchor){
        Set<DatasetProduct> related=new LinkedHashSet<>(brandIndex.getOrDefault(matcher.normalize(anchor.brand()),List.of()));
        for(String code:crossMatcher.models(anchor))related.addAll(modelIndex.getOrDefault(code,List.of()));
        return crossMatcher.candidates(anchor,List.copyOf(related));
    }
    public DatasetSnapshot snapshot() { return snapshot; }
    public List<String> categories() { return snapshot.products().stream().map(DatasetProduct::category).distinct().sorted().toList(); }
    public Page search(String query,String category,Sort sort,boolean watchOnly,Set<String> saved,int page,int pageSize) {
        return search(query,category,sort,watchOnly,saved,page,pageSize,"All stores");
    }
    public Page search(String query,String category,Sort sort,boolean watchOnly,Set<String> saved,int page,int pageSize,String store) {
        if(pageSize<1) throw new IllegalArgumentException("Page size must be positive.");
        String[] tokens=matcher.normalize(query).split(" ");
        List<DatasetProduct> found=orders.get(sort).stream().filter(p -> category.equals("All categories") || p.category().equals(category))
            .filter(p -> store.equals("All stores") || p.store().equals(store)).filter(p -> !watchOnly || saved.contains(p.recordId()))
            .filter(p -> Arrays.stream(tokens).allMatch(t -> searchable.get(p.recordId()).contains(t))).toList();
        int pages=Math.max(1,(found.size()+pageSize-1)/pageSize), selected=Math.min(Math.max(page,0),pages-1);
        int start=selected*pageSize;
        return new Page(found.subList(start,Math.min(start+pageSize,found.size())),selected,pages,found.size());
    }
}
