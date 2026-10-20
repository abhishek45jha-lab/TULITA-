package tulita.service;
import tulita.dao.CatalogDao.CatalogSnapshot;
import tulita.model.*;
import tulita.parser.*;
import tulita.matcher.ProductMatcher;
import java.util.*;
import java.math.BigDecimal;

public final class CatalogService {
    public enum Sort { LOWEST_PRICE, HIGHEST_DISCOUNT, TOP_RATED }
    private final List<ProductDetails> products;
    private final Map<Long, List<Listing>> byProduct;
    private final ProductMatcher matcher = new ProductMatcher();
    private final ComparisonService comparison = new ComparisonService();
    private final int excludedCount;
    public CatalogService(CatalogSnapshot snapshot) {
        products = snapshot.products();
        Map<String, StoreParser> parsers = new HashMap<>();
        // Dynamic dispatch through the StoreParser abstraction (no scraping).
        for (StoreParser parser : List.of(new AmazonParser(), new FlipkartParser(), new MyntraParser())) parsers.put(parser.storeName(), parser);
        Map<Long, ProductDetails> ids = new HashMap<>();
        products.forEach(p -> ids.put(p.getId(), p));
        Map<Long, List<Listing>> grouped = new HashMap<>();
        int excluded = 0;
        for (RawListing raw : snapshot.listings()) {
            StoreParser parser = parsers.get(raw.store());
            if (parser == null) throw new IllegalArgumentException("Unsupported store in catalog: " + raw.store());
            Listing listing = parser.parse(raw);
            ProductDetails p = ids.get(raw.productId());
            if (p != null && matcher.comparable(p, listing)) grouped.computeIfAbsent(p.getId(), k -> new ArrayList<>()).add(listing);
            else excluded++;
        }
        Map<Long, List<Listing>> immutable = new HashMap<>();
        grouped.forEach((id, list) -> immutable.put(id, comparison.sorted(list)));
        byProduct = Map.copyOf(immutable);
        excludedCount = excluded;
    }
    public int excludedCount() { return excludedCount; }
    public int productCount() { return products.size(); }
    public int listingCount() { return byProduct.values().stream().mapToInt(List::size).sum(); }
    public List<Listing> listings(ProductDetails p) { return byProduct.getOrDefault(p.getId(), List.of()); }
    public Optional<Listing> best(ProductDetails p) {
        return listings(p).stream().filter(Listing::isAvailable).filter(l -> l.finalPrice().isPresent()).min(comparison.finalCostOrder());
    }
    public List<ProductDetails> search(String query, String category, Sort sort, boolean watchOnly, WatchlistService watchlist) {
        Comparator<ProductDetails> order = switch (sort) {
            case LOWEST_PRICE -> Comparator.comparing(p -> best(p).flatMap(Listing::finalPrice).orElse(null), Comparator.nullsLast(Comparator.naturalOrder()));
            case HIGHEST_DISCOUNT -> Comparator.comparing((ProductDetails p) -> best(p).map(Listing::getDiscount).orElse(BigDecimal.ZERO), Comparator.reverseOrder());
            case TOP_RATED -> Comparator.comparing((ProductDetails p) -> best(p).map(Listing::getRating).orElse(BigDecimal.ZERO), Comparator.reverseOrder());
        };
        return products.stream().filter(p -> category.equals("All products") || p.getCategory().equals(category))
            .filter(p -> !watchOnly || watchlist.contains(p.getId())).filter(p -> matcher.searchMatches(p, query))
            .sorted(order.thenComparing(ProductDetails::getName)).toList();
    }
}
