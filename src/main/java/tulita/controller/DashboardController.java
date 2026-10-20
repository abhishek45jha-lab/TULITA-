package tulita.controller;

import javafx.application.Platform;
import javafx.stage.Window;
import tulita.dao.*;
import tulita.database.*;
import tulita.service.*;
import tulita.view.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Owns UI actions, the snapshot and one background catalog-loading worker. */
public final class DashboardController implements AutoCloseable {
    private final DashboardView view;
    private final WatchlistService watchlist = new WatchlistService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r,"tulita-catalog-loader"); t.setDaemon(true); return t; });
    private CatalogService catalog;
    private volatile boolean closed;
    public DashboardController(DashboardView view, Window owner, Consumer<String> openUrl) {
        this.view = view;
        view.actions(this::refresh, p -> ComparisonView.show(owner,p,catalog.listings(p),openUrl,watchlist.contains(p.getId()), () -> { watchlist.toggle(p.getId()); refresh(); }),
            p -> { watchlist.toggle(p.getId()); refresh(); }, this::load);
    }
    public void load() {
        view.showLoading();
        executor.submit(() -> {
            try {
                AppConfig config = AppConfig.load();
                CatalogDao dao = config.mode().equals("mysql") ? new JdbcCatalogDao(new ConnectionFactory(config)) : new DemoCatalogDao();
                CatalogService loaded = new CatalogService(dao.load());
                Platform.runLater(() -> { if (!closed) { catalog=loaded; view.setMode(config.mode().equals("mysql") ? "MYSQL · SAMPLE CATALOG" : "DEMO MODE · SAMPLE DATA"); refresh(); } });
            } catch (Exception e) {
                String message = e instanceof DataAccessException ? e.getMessage() : "Configuration or catalog is invalid. Check application.properties and the supplied seed files; no data was saved.";
                Platform.runLater(() -> { if (!closed) { view.setMode("SETUP REQUIRED"); view.showError(message); } });
            }
        });
    }
    private void refresh() {
        if (catalog != null) view.showProducts(catalog.search(view.query(),view.category(),view.sortOrder(),view.watchOnly(),watchlist),catalog,watchlist);
    }
    @Override public void close() { closed=true; executor.shutdownNow(); }
}
