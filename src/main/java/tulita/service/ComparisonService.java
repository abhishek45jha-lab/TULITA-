package tulita.service;
import tulita.model.Listing;
import java.util.*;
import java.math.BigDecimal;
public final class ComparisonService {
    public Comparator<Listing> finalCostOrder() {
        return Comparator.comparing((Listing l) -> !l.isAvailable())
            .thenComparing(l -> l.finalPrice().orElse(null), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Listing::getRating, Comparator.reverseOrder()).thenComparing(Listing::getId);
    }
    public List<Listing> sorted(List<Listing> listings) { return listings.stream().sorted(finalCostOrder()).toList(); }
    public Optional<BigDecimal> lowestKnownTotal(List<Listing> listings) {
        return listings.stream().filter(Listing::isAvailable).flatMap(l -> l.finalPrice().stream()).min(BigDecimal::compareTo);
    }
    public boolean isBest(Listing listing, List<Listing> listings) {
        return listing.isAvailable() && listing.finalPrice().isPresent()
            && lowestKnownTotal(listings).map(p -> listing.finalPrice().orElseThrow().compareTo(p) == 0).orElse(false);
    }
    public boolean hasUnknownAvailableTotal(List<Listing> listings) {
        return listings.stream().anyMatch(l -> l.isAvailable() && l.finalPrice().isEmpty());
    }
}
