package tulita.service;
import java.util.*;
/** Session-only, deliberately not represented as a persistent MySQL write. */
public final class WatchlistService {
    private final Set<Long> ids = new HashSet<>();
    public boolean contains(long id) { return ids.contains(id); }
    public boolean toggle(long id) { if (ids.remove(id)) return false; ids.add(id); return true; }
    public int size() { return ids.size(); }
}
