package tulita.util;
import java.net.URI;
import java.util.*;
public final class StoreLinks {
    private StoreLinks() { }
    public static boolean isAllowed(String store, String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String expected = Map.of("Amazon","amazon.in", "Flipkart","flipkart.com", "Myntra","myntra.com").get(store);
            String host = uri.getHost();
            return expected != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                && (host.equalsIgnoreCase(expected) || host.toLowerCase(Locale.ROOT).endsWith("." + expected));
        } catch (IllegalArgumentException e) { return false; }
    }
}
