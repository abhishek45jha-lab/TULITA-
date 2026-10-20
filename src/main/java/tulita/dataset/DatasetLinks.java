package tulita.dataset;
import java.net.URI;
import java.util.Optional;
import tulita.util.StoreLinks;
public final class DatasetLinks {
    private DatasetLinks() { }
    /** Original dataset contains HTTP links. Upgrade only validated Flipkart hosts to HTTPS. */
    public static Optional<String> safeProductUrl(String raw){return safeProductUrl("Flipkart",raw);}
    public static Optional<String> safeProductUrl(String store,String raw) {
        try {
            URI uri=URI.create(raw);
            if(!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) return Optional.empty();
            if(uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) return Optional.empty();
            URI secure=new URI("https",null,uri.getHost(),-1,uri.getPath(),uri.getQuery(),uri.getFragment());
            return StoreLinks.isAllowed(store,secure.toString()) ? Optional.of(secure.toString()) : Optional.empty();
        } catch(Exception e) { return Optional.empty(); }
    }
}
