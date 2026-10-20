package tulita.matcher;

import tulita.model.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Candidate similarity is not identity. Metadata checks are mandatory before comparison. */
public final class ProductMatcher {
    private static final Set<String> NOISE = Set.of("the", "for", "with", "and", "shoes", "shoe", "running", "mens", "womens", "men", "women");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    public String normalize(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    private Set<String> words(String title) {
        return Arrays.stream(normalize(title).split(" ")).filter(s -> !s.isBlank() && !NOISE.contains(s)).collect(Collectors.toSet());
    }
    private Set<String> numbers(String title) {
        Set<String> result = new HashSet<>();
        var m = NUMBER.matcher(normalize(title));
        while (m.find()) result.add(m.group());
        return result;
    }
    public boolean potentiallySimilar(String a, String b) {
        var left = words(a); var right = words(b);
        if (left.isEmpty() || right.isEmpty() || !numbers(a).equals(numbers(b))) return false;
        Set<String> qualifiers = Set.of("pro", "max", "plus", "ultra", "mini", "lite", "fe", "se");
        for (String qualifier : qualifiers) if (left.contains(qualifier) != right.contains(qualifier)) return false;
        long common = left.stream().filter(right::contains).count();
        return common >= 2 && (double) common / Math.min(left.size(), right.size()) >= 0.70;
    }
    public boolean comparable(ProductDetails product, Listing listing) {
        return product.getId() == listing.getProductId()
            && normalize(product.getModel()).equals(normalize(listing.getModel()))
            && normalize(product.getVariant()).equals(normalize(listing.getVariant()))
            && words(listing.getTitle()).containsAll(words(product.getBrand()))
            && potentiallySimilar(product.getName(), listing.getTitle());
    }
    public boolean searchMatches(ProductDetails product, String query) {
        String haystack = normalize(product.getName() + " " + product.getBrand() + " " + product.getCategory() + " " + product.getVariant());
        return Arrays.stream(normalize(query).split(" ")).allMatch(haystack::contains);
    }
}
