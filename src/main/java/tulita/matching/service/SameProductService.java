package tulita.matching.service;
import tulita.dataset.model.*;
import tulita.dataset.service.*;
import tulita.matching.model.*;
import tulita.matcher.ProductMatcher;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/** Comparison is membership in an evidence-backed reviewed canonical variant, NEVER title similarity. */
public final class SameProductService {
    public static final List<String> STORES=List.of("Amazon","Flipkart","Myntra");
    private final DatasetSnapshot snapshot;
    private final List<MatchedProduct> groups;
    private final Map<String,MatchedProduct> byRecord;
    private final Map<String,String> text=new HashMap<>();
    private final ProductMatcher matcher=new ProductMatcher();
    public SameProductService(DatasetSnapshot snapshot,List<ReviewedLink> links){
        this.snapshot=snapshot;
        Map<String,DatasetProduct> records=new HashMap<>();snapshot.products().forEach(p->records.put(p.recordId(),p));
        Map<String,CanonicalProduct> identities=new HashMap<>();
        Map<String,CanonicalProduct> definitions=new LinkedHashMap<>();Map<String,Map<String,DatasetProduct>> grouped=new LinkedHashMap<>();Map<String,Map<String,ReviewedLink>> reviews=new LinkedHashMap<>();Set<String> used=new HashSet<>();
        for(ReviewedLink link:links){
            validate(link,records);
            var identity=identities.putIfAbsent(link.product().id(),link.product());
            if(identity!=null&&(!identity.brand().equals(link.product().brand())||!identity.model().equals(link.product().model())||!identity.name().equals(link.product().name())||!identity.category().equals(link.product().category())))fail("One canonical ID must retain the same name, brand, model and category across variants.");
            String key=link.product().key();CanonicalProduct prior=definitions.putIfAbsent(key,link.product());
            if(prior!=null&&!prior.equals(link.product()))fail("Conflicting canonical definition for "+key);
            if(!used.add(link.recordId()))fail("A source record cannot be assigned to multiple canonical variants.");
            if(grouped.computeIfAbsent(key,k->new LinkedHashMap<>()).putIfAbsent(link.store(),records.get(link.recordId()))!=null)fail("More than one observation for the same store/variant. Choose one reviewed observation.");
            reviews.computeIfAbsent(key,k->new LinkedHashMap<>()).put(link.store(),link);
        }
        List<MatchedProduct> result=new ArrayList<>();Map<String,MatchedProduct> index=new HashMap<>();CrossStoreMatcher guard=new CrossStoreMatcher();
        for(String key:definitions.keySet()){
            List<DatasetProduct> rows=new ArrayList<>(grouped.get(key).values());
            for(int i=0;i<rows.size();i++)for(int j=i+1;j<rows.size();j++)if(guard.assess(rows.get(i),rows.get(j)).status()==CrossStoreMatcher.Status.CONFLICT)fail("Known source variant/model conflict in "+key);
            MatchedProduct group=new MatchedProduct(definitions.get(key),grouped.get(key),reviews.get(key));result.add(group);group.listings().values().forEach(p->index.put(p.recordId(),group));
            String aliases=group.listings().values().stream().map(p->p.title()+" "+p.productId()).collect(Collectors.joining(" "));
            text.put(key,matcher.normalize(group.product().name()+" "+group.product().brand()+" "+group.product().model()+" "+group.product().variantKey()+" "+aliases));
        }
        groups=List.copyOf(result);byRecord=Map.copyOf(index);
        snapshot.products().forEach(p->text.put("row:"+p.recordId(),matcher.normalize(p.title()+" "+p.brand()+" "+p.categoryPath()+" "+p.productId())));
    }
    private String n(String s){return matcher.normalize(s==null?"":s);}
    private boolean missing(String s){return s==null||n(s).isBlank()||Set.of("unknown","na","n a","none","same").contains(n(s));}
    private void validate(ReviewedLink link,Map<String,DatasetProduct> records){
        CanonicalProduct c=Objects.requireNonNull(link.product());
        if(missing(c.id())||missing(c.name())||missing(c.brand())||missing(c.model())||missing(c.category())||missing(c.variantKey()))fail("Canonical ID/name/brand/model/category/variant must be supplied, not unknown.");
        if(c.id().length()>80||c.variantKey().length()>160||c.name().length()>500||c.brand().length()>200||c.model().length()>200||c.category().length()>200||java.util.stream.Stream.of(c.size(),c.color(),c.capacity(),c.quantity(),c.gender()).anyMatch(v->v.length()>150))fail("Canonical ID or variant key is too long.");
        if(!STORES.contains(link.store()))fail("Unsupported store in mapping.");
        DatasetProduct p=records.get(link.recordId());if(p==null||!p.store().equals(link.store()))fail("Unknown record ID or wrong store: "+link.recordId());
        if(!RecordFingerprint.of(p).equals(link.recordSha256()))fail("Source observation changed or fingerprint is incorrect: "+link.recordId());
        if(link.reviewedOn()==null||link.reviewedOn().isAfter(java.time.LocalDate.now())||missing(link.reviewNote())||link.reviewNote().length()<20)fail("Review date and meaningful evidence note are required.");
        try{URI uri=URI.create(link.evidenceUrl());if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)fail("Evidence must be a valid HTTPS URL.");}catch(RuntimeException e){fail("Evidence must be a valid HTTPS URL.");}
        if(!missing(p.brand())&&!n(p.brand()).equals("generic")&&!n(p.brand()).equals(n(c.brand())))fail("Canonical brand conflicts with the source.");
        if(!(" "+n(p.title())+" ").contains(" "+n(c.model())+" "))fail("Canonical manufacturer model must occur as a complete phrase/code in every source title; do not use a retailer SKU.");
        check(c.size(),p.provenance().size(),"size");check(c.color(),p.provenance().color(),"colour");check(c.gender(),p.provenance().gender(),"gender/fit");check(c.quantity(),p.provenance().quantity(),"pack quantity");
        CrossStoreMatcher attributes=new CrossStoreMatcher();
        var sourceColors=attributes.colorsOf(p.provenance().color().isBlank()?p.title():p.provenance().color());
        if(!sourceColors.isEmpty()&&!sourceColors.equals(attributes.colorsOf(c.color())))fail("Reviewed colour conflicts with or omits explicit source-title colour.");
        var sourceGender=attributes.genderOf(p.provenance().gender().isBlank()?p.title():p.provenance().gender());
        if(!sourceGender.isEmpty()&&!sourceGender.equals(attributes.genderOf(c.gender())))fail("Reviewed gender/fit conflicts with explicit source title.");
        var sourceCapacity=attributes.capacitiesOf(p.title());
        if(!sourceCapacity.isEmpty()&&!sourceCapacity.equals(attributes.capacitiesOf(c.capacity())))fail("Reviewed capacity omits or conflicts with explicit source-title units. No automatic unit conversion is performed.");
        var pack=java.util.regex.Pattern.compile("(?i)\\b(?:pack|set)\\s+of\\s+(\\d+)\\b|\\b(\\d+)\\s*[- ]?pack\\b").matcher(p.title());
        while(pack.find()){String number=pack.group(1)==null?pack.group(2):pack.group(1);if(!java.util.regex.Pattern.compile("\\b"+number+"\\b").matcher(c.quantity()).find())fail("Reviewed pack quantity conflicts with or omits explicit title pack count.");}
        var sizePattern=java.util.regex.Pattern.compile("(?i)\\b(UK|US|EU|EUR|size)\\s*[:=-]?\\s*(\\d+(?:\\.\\d+)?|XXXL|XXL|XXS|XL|XS|S|M|L)\\b");
        var titleSize=sizePattern.matcher(p.title());
        while(titleSize.find()){
            String system=titleSize.group(1).toLowerCase(Locale.ROOT).replace("eur","eu"),value=titleSize.group(2).toLowerCase(Locale.ROOT);boolean agreed=false;var declared=sizePattern.matcher(c.size());
            while(declared.find())if(value.equalsIgnoreCase(declared.group(2))&&(system.equals("size")||system.equals(declared.group(1).toLowerCase(Locale.ROOT).replace("eur","eu"))))agreed=true;
            if(system.equals("size")&&n(c.size()).equals(value))agreed=true;
            if(!agreed)fail("Reviewed size conflicts with or omits explicit title size/system.");
        }
        String category=n(c.category()+" "+p.category()+" "+p.title());
        if(category.contains("footwear")||category.contains("clothing")||category.matches(".*\\b(shoe|shoes|sneakers|shirt|shirts|dress|kurta|jeans|trousers)\\b.*")){if(missing(c.size())||missing(c.color())||missing(c.gender()))fail("Fashion variants require reviewed size, colour and gender/fit.");}
        if(category.contains("mobile")||category.contains("phone")){if(missing(c.capacity())||missing(c.color()))fail("Phone variants require reviewed storage/capacity and colour.");}
    }
    private void check(String canonical,String recorded,String field){if(!missing(recorded)&&!n(canonical).equals(n(recorded)))fail("Reviewed "+field+" conflicts with or omits known source metadata.");}
    private static void fail(String message){throw new IllegalArgumentException(message);}
    public Optional<MatchedProduct> groupFor(String recordId){return Optional.ofNullable(byRecord.get(recordId));}
    public List<MatchedProduct> groups(){return groups;}
    public long threeStoreCount(){return groups.stream().filter(MatchedProduct::allThree).count();}
    public Set<String> recordIdsFor(String id){return groupFor(id).map(g->g.listings().values().stream().map(DatasetProduct::recordId).collect(Collectors.toSet())).orElse(Set.of(id));}
    public DatasetService.Page search(String query,String category,DatasetService.Sort sort,boolean watchOnly,Set<String> saved,int page,int pageSize,String store,boolean threeOnly){
        if(pageSize<1)throw new IllegalArgumentException("Page size must be positive.");
        String[] terms=n(query).split(" ");List<DatasetProduct> selected=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(DatasetProduct p:snapshot.products()){
            MatchedProduct g=byRecord.get(p.recordId());if(threeOnly&&(g==null||!g.allThree()))continue;
            if(!store.equals("All stores")&&!p.store().equals(store))continue;
            if(!category.equals("All categories")&&!(g==null?p.category().equals(category):g.product().category().equals(category)||g.listings().values().stream().anyMatch(l->l.category().equals(category))))continue;
            Set<String> ids=g==null?Set.of(p.recordId()):recordIdsFor(p.recordId());if(watchOnly&&ids.stream().noneMatch(saved::contains))continue;
            String key=g==null?"row:"+p.recordId():g.product().key();if(!Arrays.stream(terms).allMatch(text.get(key)::contains)||!seen.add(key))continue;selected.add(p);
        }
        Comparator<DatasetProduct> order=switch(sort){
            case LOWEST_PRICE->Comparator.comparing(p->metric(p,0),Comparator.nullsLast(Comparator.naturalOrder()));
            case HIGHEST_DISCOUNT->Comparator.comparing(p->metric(p,1),Comparator.nullsLast(Comparator.reverseOrder()));
            case TOP_RATED->Comparator.comparing(p->metric(p,2),Comparator.nullsLast(Comparator.reverseOrder()));
        };
        selected.sort(order.thenComparing(DatasetProduct::title).thenComparing(DatasetProduct::recordId));
        int pages=Math.max(1,(selected.size()+pageSize-1)/pageSize),number=Math.min(Math.max(page,0),pages-1),start=number*pageSize;
        return new DatasetService.Page(selected.subList(start,Math.min(start+pageSize,selected.size())),number,pages,selected.size());
    }
    public BigDecimal metric(DatasetProduct p,int type){
        var rows=groupFor(p.recordId()).map(g->g.listings().values()).orElse(List.of(p));
        var values=rows.stream().map(l->type==0?l.price():type==1?l.discount().orElse(null):l.rating()).filter(Objects::nonNull);
        return type==0?values.min(BigDecimal::compareTo).orElse(null):values.max(BigDecimal::compareTo).orElse(null);
    }
}
