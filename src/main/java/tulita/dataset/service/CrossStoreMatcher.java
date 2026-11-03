package tulita.dataset.service;

import tulita.dataset.model.*;
import tulita.matcher.ProductMatcher;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A candidate suggestion is NEVER a verified variant. Missing evidence cannot count as equality. */
public final class CrossStoreMatcher {
    public enum Status { SAME_VARIANT, MODEL_MATCH, CANDIDATE, CONFLICT, UNRELATED }
    public record Assessment(Status status,double score,String reason){}
    private static final Pattern MODEL=Pattern.compile("(?i)\\b(?=[a-z0-9-]{5,}\\b)(?=[a-z0-9-]*[a-z])(?=[a-z0-9-]*[0-9])[a-z0-9]+(?:-[a-z0-9]+)*\\b");
    private final ProductMatcher normalizer=new ProductMatcher();
    private static final Set<String> STOP=Set.of("men","mens","women","womens","for","with","and","the","of","a","an","unisex");
    private static final Set<String> COLORS=Set.of("black","white","blue","red","green","yellow","pink","grey","gray","brown","beige","silver","gold","navy","orange","purple");
    private String n(String s){return normalizer.normalize(s);}
    private Set<String> words(String title){return Arrays.stream(n(title).split(" ")).filter(s->!s.isBlank()&&!STOP.contains(s)).collect(Collectors.toSet());}
    public Set<String> models(DatasetProduct p){
        Set<String> codes=new HashSet<>();
        var m=MODEL.matcher(p.title());
        while(m.find()){
            String code=m.group().toLowerCase(Locale.ROOT);
            if(code.chars().filter(Character::isDigit).count()>=2&&!code.matches("\\d+(gb|tb|mb|ml|kg|gm|inch|cm|mm|pcs|pack)"))codes.add(code);
        }
        return codes;
    }
    public Set<String> colorsOf(String value){Set<String> found=words(value);found.retainAll(COLORS);if(found.remove("gray"))found.add("grey");return found;}
    private Set<String> colors(DatasetProduct p){return colorsOf(p.provenance().color().isBlank()?p.title():p.provenance().color());}
    public String genderOf(String value){
        String g=n(value);if(g.contains("unisex"))return "unisex";Set<String> groups=new TreeSet<>();
        if(g.matches(".*\\b(women|womens|woman)\\b.*"))groups.add("women");
        if(g.matches(".*\\b(men|mens|man)\\b.*"))groups.add("men");
        if(g.matches(".*\\b(girl|girls)\\b.*"))groups.add("girls");
        if(g.matches(".*\\b(boy|boys)\\b.*"))groups.add("boys");return String.join(",",groups);
    }
    private String gender(DatasetProduct p){return genderOf(p.provenance().gender().isBlank()?p.title():p.provenance().gender());}
    public Set<String> capacitiesOf(String value){Set<String>s=new HashSet<>();var m=Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(gb|tb|ml|kg|gm|g|litre|liter|l)\\b").matcher(value);while(m.find())s.add(m.group(1)+m.group(2).toLowerCase(Locale.ROOT));return s;}
    private Set<String> capacities(DatasetProduct p){return capacitiesOf(p.title());}
    private String kind(DatasetProduct p){String t=n(p.title()+" "+p.category());if(t.contains("watch"))return "watch";if(t.matches(".*\\b(shoes|shoe|sneakers|sneaker|sandals|footwear)\\b.*"))return "footwear";if(t.matches(".*\\b(shirt|shirts|kurta|jeans|dress|clothing|top|tops|trousers)\\b.*"))return "clothing";if(t.matches(".*\\b(phone|iphone|smartphone|mobile)\\b.*"))return "mobile";return "other";}
    public Assessment assess(DatasetProduct a,DatasetProduct b){
        if(a.store().equals(b.store()))return new Assessment(Status.UNRELATED,0,"Same-store observations are not cross-store matches.");
        String ba=n(a.brand()),bb=n(b.brand());
        boolean knownA=!ba.isBlank()&&!Set.of("generic","unknown","na").contains(ba),knownB=!bb.isBlank()&&!Set.of("generic","unknown","na").contains(bb);
        if(knownA&&knownB&&!ba.equals(bb))return new Assessment(Status.UNRELATED,0,"Explicit brands differ.");
        Set<String> wa=words(a.title()),wb=words(b.title());long shared=wa.stream().filter(wb::contains).count();
        double score=wa.isEmpty()||wb.isEmpty()?0:(double)shared/Math.max(wa.size(),wb.size());
        Set<String> ma=models(a),mb=models(b),common=new HashSet<>(ma);common.retainAll(mb);
        if(!ma.isEmpty()&&!mb.isEmpty()&&common.isEmpty())return new Assessment(Status.CONFLICT,score,"Different explicit model codes.");
        for(String qualifier:Set.of("pro","max","plus","ultra","mini","lite","fe","se"))if(wa.contains(qualifier)!=wb.contains(qualifier))return new Assessment(Status.CONFLICT,score,"Different explicit model qualifier.");
        String quantityA=n(a.provenance().quantity()),quantityB=n(b.provenance().quantity());
        if(!quantityA.isEmpty()&&!quantityB.isEmpty()&&!quantityA.equals(quantityB))return new Assessment(Status.CONFLICT,score,"Explicit pack/quantity differs.");
        Set<String> ca=colors(a),cb=colors(b);String ga=gender(a),gb=gender(b),sa=n(a.provenance().size()),sb=n(b.provenance().size());
        if(!ca.isEmpty()&&!cb.isEmpty()&&!ca.equals(cb))return new Assessment(Status.CONFLICT,score,"Colour differs.");
        if(!ga.isEmpty()&&!gb.isEmpty()&&!ga.equals(gb))return new Assessment(Status.CONFLICT,score,"Gender/fit group differs.");
        if(!sa.isEmpty()&&!sb.isEmpty()&&!sa.equals(sb))return new Assessment(Status.CONFLICT,score,"Explicit size differs.");
        Set<String> qa=capacities(a),qb=capacities(b);
        if(!qa.isEmpty()&&!qb.isEmpty()&&!qa.equals(qb))return new Assessment(Status.CONFLICT,score,"Capacity/quantity differs.");
        String ka=kind(a),kb=kind(b);
        if(!ka.equals("other")&&!kb.equals("other")&&!ka.equals(kb))return new Assessment(Status.CONFLICT,score,"Product types differ.");
        if(!common.isEmpty()){
            if(!knownA||!knownB||!ma.equals(mb))return new Assessment(Status.MODEL_MATCH,Math.max(.8,score),"Shared explicit model token; brand or model-alias evidence is incomplete. Variant is unverified.");
            boolean fashion=ka.equals("footwear")||ka.equals("clothing");
            boolean complete=!ka.equals("other") && (quantityA.isEmpty()==quantityB.isEmpty()) && (!fashion||(!sa.isEmpty()&&!sb.isEmpty()&&!ca.isEmpty()&&!cb.isEmpty()&&!ga.isEmpty()&&!gb.isEmpty()))
                && (!ka.equals("mobile")||(!qa.isEmpty()&&!qb.isEmpty()&&!ca.isEmpty()&&!cb.isEmpty()))
                && (qa.isEmpty()==qb.isEmpty()) && (!ca.isEmpty()&&!cb.isEmpty());
            return new Assessment(complete?Status.SAME_VARIANT:Status.MODEL_MATCH,Math.max(.8,score),complete?"Same brand + explicit model code + required recorded variant attributes; historical, not externally verified.":"Same explicit model code; size/colour/capacity evidence is incomplete. Not a verified variant.");
        }
        if(knownA&&knownB&&shared>=2&&score>=.45)return new Assessment(Status.CANDIDATE,score,"Title/brand similarity only. Model and variant identity are unverified.");
        return new Assessment(Status.UNRELATED,score,"Insufficient identity evidence.");
    }
    public List<DatasetProduct> candidates(DatasetProduct anchor,List<DatasetProduct> all){
        List<DatasetProduct> result=new ArrayList<>();result.add(anchor);
        for(String store:List.of("Amazon","Flipkart","Myntra")){
            if(store.equals(anchor.store()))continue;
            all.stream().filter(p->p.store().equals(store)).map(p->Map.entry(p,assess(anchor,p)))
                .filter(e->Set.of(Status.SAME_VARIANT,Status.MODEL_MATCH,Status.CANDIDATE).contains(e.getValue().status()))
                .sorted(Comparator.<Map.Entry<DatasetProduct,Assessment>>comparingInt(e->e.getValue().status().ordinal()).thenComparing(e->e.getValue().score(),Comparator.reverseOrder()).thenComparing(e->e.getKey().recordId()))
                .limit(3).map(Map.Entry::getKey).forEach(result::add);
        }
        return List.copyOf(result);
    }
}
