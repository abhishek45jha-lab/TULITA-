package tulita.database;
import java.sql.*;
public final class ConnectionFactory {
    private final AppConfig config;
    public ConnectionFactory(AppConfig config) { this.config = config; }
    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }
}
