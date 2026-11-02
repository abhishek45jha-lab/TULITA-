package tulita.budget.service;

import tulita.budget.model.BudgetCategory;
import tulita.budget.model.BudgetProduct;
import tulita.budget.model.ScoredProduct;
import tulita.dataset.model.DatasetProduct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a clean planning catalogue from existing {@link DatasetProduct} observations.
 * It classifies four electronics categories, collapses identical source rows, extracts recorded
 * specifications and computes deterministic quality/value scores. No price or product is created.
 */
public final class BudgetCatalogService {
    private static final List<String> KNOWN_BRANDS = List.of(
            "HP", "Dell", "Lenovo", "Asus", "Acer", "Apple", "MSI", "Samsung", "Sony", "JBL",
            "boAt", "Logitech", "Microsoft", "Xiaomi", "Realme", "OnePlus", "Boult", "Noise",
            "pTron", "Zebronics", "Redragon", "Razer", "Corsair", "EPOMAKER", "Sennheiser",
            "Audio-Technica", "Bose", "Philips", "Panasonic", "LG", "Toshiba", "Fujitsu",
            "Infinix", "Honor", "Huawei", "Motorola", "Nokia", "Amkette", "Intex", "iBall",
            "Compaq", "Alienware", "HyperX", "SteelSeries", "Skullcandy", "Jabra", "Marshall",
            "Portronics", "TAGG", "Ant Esports", "Cosmic Byte", "Keychron", "Royal Kludge",
            "Womier", "Gamdias", "Kreo", "Turtle Beach", "Astro", "Beyerdynamic", "Oppo", "Vivo");

    private final Map<BudgetCategory, List<ScoredProduct>> byCategory;
    private final Map<String, Integer> brandCounts;
    private final int consideredRecords;
    private final int duplicatesCollapsed;

    public BudgetCatalogService(List<DatasetProduct> source) {
        Objects.requireNonNull(source, "source");
        EnumMap<BudgetCategory, List<Candidate>> grouped = new EnumMap<>(BudgetCategory.class);
        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        for (DatasetProduct record : source) {
            Optional<BudgetCategory> category = BudgetClassifier.classify(record);
            if (category.isEmpty()) continue;
            BigDecimal price = record.price().setScale(2, RoundingMode.HALF_UP);
            String key = record.store() + "|" + SourceText.normalize(record.title()) + "|"
                    + price.stripTrailingZeros().toPlainString();
            if (!seen.add(key)) {
                duplicates++;
                continue;
            }
            SpecProfile spec = SpecExtractor.extract(category.get(), record.title(), record.specifications(), record.description());
            BudgetProduct product = new BudgetProduct(
                    record.recordId(), record.title(), resolveBrand(record), category.get(), record.store(),
                    price, record.mrp(), record.rating(), record.observedAt(), record.url(),
                    record.provenance() == null ? "" : record.provenance().license(),
                    record.provenance() == null ? "" : record.provenance().dateNote(),
                    spec.display(category.get()), spec.renewed());
            grouped.computeIfAbsent(category.get(), k -> new ArrayList<>()).add(new Candidate(product, spec));
        }

        EnumMap<BudgetCategory, List<ScoredProduct>> scored = new EnumMap<>(BudgetCategory.class);
        for (Map.Entry<BudgetCategory, List<Candidate>> entry : grouped.entrySet()) {
            List<Candidate> candidates = entry.getValue();
            Map<Candidate, ProductScorer.Result> qualities = new IdentityHashMap<>();
            BigDecimal best = BigDecimal.ZERO;
            for (Candidate candidate : candidates) {
                ProductScorer.Result result = ProductScorer.quality(candidate.product, candidate.spec);
                qualities.put(candidate, result);
                BigDecimal efficiency = ProductScorer.efficiency(result.quality(), candidate.product.price());
                if (efficiency.compareTo(best) > 0) best = efficiency;
            }
            List<Candidate> ranked = new ArrayList<>(candidates);
            ranked.sort(Comparator.comparing((Candidate c) -> ProductScorer.efficiency(qualities.get(c).quality(), c.product.price()))
                    .reversed().thenComparing(c -> c.product.recordId()));
            Map<Candidate, Integer> ranks = new IdentityHashMap<>();
            for (int i = 0; i < ranked.size(); i++) ranks.put(ranked.get(i), i + 1);

            List<ScoredProduct> rows = new ArrayList<>();
            for (Candidate candidate : candidates) {
                ProductScorer.Result quality = qualities.get(candidate);
                int valueScore = ProductScorer.valueScore(quality.quality(), candidate.product.price(), best);
                rows.add(new ScoredProduct(candidate.product, quality.quality(), valueScore,
                        ranks.get(candidate), candidates.size(), quality.factors()));
            }
            rows.sort(Comparator.comparingInt(ScoredProduct::qualityScore).reversed()
                    .thenComparing(s -> s.product().price())
                    .thenComparing(s -> s.product().recordId()));
            scored.put(entry.getKey(), List.copyOf(rows));
        }
        EnumMap<BudgetCategory, List<ScoredProduct>> immutable = new EnumMap<>(BudgetCategory.class);
        immutable.putAll(scored);
        byCategory = Collections.unmodifiableMap(immutable);

        Map<String, Integer> brands = new HashMap<>();
        for (List<ScoredProduct> rows : byCategory.values()) {
            for (ScoredProduct row : rows) {
                String brand = row.product().brand();
                if (!"Not stated".equals(brand)) brands.merge(brand, 1, Integer::sum);
            }
        }
        brandCounts = Collections.unmodifiableMap(brands);
        consideredRecords = byCategory.values().stream().mapToInt(List::size).sum() + duplicates;
        duplicatesCollapsed = duplicates;
    }

    public List<ScoredProduct> products(BudgetCategory category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public int count(BudgetCategory category) {
        return products(category).size();
    }

    public Map<BudgetCategory, Integer> counts() {
        EnumMap<BudgetCategory, Integer> counts = new EnumMap<>(BudgetCategory.class);
        for (BudgetCategory category : BudgetCategory.values()) counts.put(category, count(category));
        return Collections.unmodifiableMap(counts);
    }

    public List<String> brands() {
        return brandCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .map(Map.Entry::getKey).toList();
    }

    public Map<String, Integer> brandCounts() {
        return brandCounts;
    }

    public List<String> brandsFor(Collection<BudgetCategory> categories) {
        Map<String, Integer> counts = new HashMap<>();
        for (BudgetCategory category : categories) {
            for (ScoredProduct row : products(category)) {
                if (!"Not stated".equals(row.product().brand()))
                    counts.merge(row.product().brand(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .map(Map.Entry::getKey).toList();
    }

    public int totalProducts() {
        return byCategory.values().stream().mapToInt(List::size).sum();
    }

    public int consideredRecords() {
        return consideredRecords;
    }

    public int duplicatesCollapsed() {
        return duplicatesCollapsed;
    }

    public Set<BudgetCategory> availableCategories() {
        return byCategory.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String resolveBrand(DatasetProduct record) {
        String supplied = record.brand() == null ? "" : record.brand().strip();
        if (validBrand(supplied, record.title())) return supplied;
        String title = record.title().toLowerCase(Locale.ROOT).strip();
        for (String brand : KNOWN_BRANDS) {
            String known = brand.toLowerCase(Locale.ROOT);
            if (title.startsWith(known + " ") || title.startsWith(known + "-")) return brand;
        }
        return "Not stated";
    }

    private static boolean validBrand(String brand, String title) {
        if (brand.isBlank() || brand.length() > 32) return false;
        if (brand.equalsIgnoreCase(title)) return false;
        if (brand.split("\\s+").length > 4) return false;
        return !(title.startsWith(brand) && brand.length() > 24);
    }

    private record Candidate(BudgetProduct product, SpecProfile spec) { }
}
