package com.simulator.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages JDBC connections to the MySQL database.
 * <p>
 * Reads configuration from {@code db.properties} on the classpath.
 * If the file is missing, sensible defaults are used (localhost MySQL).
 * <p>
 * All DAO classes obtain connections through {@link #getConnection()}.
 */
public class DBConnection {

    private static final Logger LOG = LoggerFactory.getLogger(DBConnection.class);

    private static String url;
    private static String username;
    private static String password;
    private static boolean initialised = false;

    static {
        try (InputStream is = DBConnection.class
                .getClassLoader()
                .getResourceAsStream("db.properties")) {

            Properties props = new Properties();
            if (is != null) {
                props.load(is);
            } else {
                LOG.warn("db.properties not found – using defaults");
                props.setProperty("db.url",
                    "jdbc:mysql://localhost:3306/tv_simulator"
                  + "?createDatabaseIfNotExist=true&useSSL=false"
                  + "&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                props.setProperty("db.username", "root");
                props.setProperty("db.password", "");
            }

            url      = props.getProperty("db.url");
            username = props.getProperty("db.username");
            password = props.getProperty("db.password");

            // Load the MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            initialised = true;
            LOG.info("DBConnection initialised: {}", url);

        } catch (Exception e) {
            LOG.error("Failed to initialise DBConnection: {}", e.getMessage());
            // App will still run – DB features degrade gracefully
        }
    }

    /**
     * @return a new JDBC connection, or {@code null} if the database is
     *         unavailable.
     */
    public static Connection getConnection() throws SQLException {
        if (!initialised) {
            throw new SQLException("DBConnection not initialised (driver missing?)");
        }
        return DriverManager.getConnection(url, username, password);
    }

    /** @return true if the database driver loaded successfully. */
    public static boolean isAvailable() {
        if (!initialised) return false;
        try (Connection c = getConnection()) {
            return c != null;
        } catch (SQLException e) {
            return false;
        }
    }
}
