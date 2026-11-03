package tulita.database;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Environment overrides local properties; passwords are never displayed or logged. */
public record AppConfig(String mode, String url, String user, String password) {
    public static AppConfig load() throws IOException {
        Properties p = new Properties();
        Path path = Path.of("application.properties");
        if (Files.exists(path)) try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        String mode = value(p, "TULITA_MODE", "app.mode", "catalog").toLowerCase(Locale.ROOT);
        if (!Set.of("demo", "mysql", "dataset", "multistore", "catalog").contains(mode)) throw new IOException("TULITA_MODE/app.mode must be catalog, multistore, dataset, demo or mysql.");
        return new AppConfig(mode, value(p,"TULITA_DB_URL","db.url","jdbc:mysql://localhost:3306/tulita?connectTimeout=5000&socketTimeout=10000"),
            value(p,"TULITA_DB_USER","db.user","tulita_app"), value(p,"TULITA_DB_PASSWORD","db.password",""));
    }
    private static String value(Properties p, String env, String key, String fallback) {
        return System.getenv().getOrDefault(env, p.getProperty(key, fallback));
    }
    @Override public String toString() { return "AppConfig[mode=" + mode + ", credentials=REDACTED]"; }
}
