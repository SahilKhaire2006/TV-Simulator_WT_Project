package com.simulator.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates all database tables at application startup if they don't exist.
 * This avoids requiring manual SQL execution – the app self-initialises.
 */
public class SchemaInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    private static final String[] DDL = {
        // ── Users ──────────────────────────────────────────────────────────
        """
        CREATE TABLE IF NOT EXISTS users (
            id         INT AUTO_INCREMENT PRIMARY KEY,
            username   VARCHAR(50)  UNIQUE NOT NULL,
            password   VARCHAR(255) NOT NULL,
            role       VARCHAR(20)  DEFAULT 'user',
            created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
        )""",

        // ── Sessions ───────────────────────────────────────────────────────
        """
        CREATE TABLE IF NOT EXISTS sessions (
            token      VARCHAR(255) PRIMARY KEY,
            user_id    INT          NOT NULL,
            created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
            expires_at TIMESTAMP    NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",

        // ── Favorites ──────────────────────────────────────────────────────
        """
        CREATE TABLE IF NOT EXISTS favorites (
            id             INT AUTO_INCREMENT PRIMARY KEY,
            user_id        INT NOT NULL,
            channel_number INT NOT NULL,
            added_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY uniq_user_channel (user_id, channel_number),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",

        // ── Visit history ──────────────────────────────────────────────────
        """
        CREATE TABLE IF NOT EXISTS visit_history (
            id             INT AUTO_INCREMENT PRIMARY KEY,
            user_id        INT,
            channel_number  INT NOT NULL,
            visit_count     INT DEFAULT 1,
            last_visited    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uniq_user_channel (user_id, channel_number),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
        )""",

        // ── Parental control ───────────────────────────────────────────────
        """
        CREATE TABLE IF NOT EXISTS parental_control (
            id             INT AUTO_INCREMENT PRIMARY KEY,
            user_id        INT NOT NULL,
            channel_number  INT NOT NULL,
            is_locked       BOOLEAN DEFAULT TRUE,
            UNIQUE KEY uniq_user_channel (user_id, channel_number),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",

        // ── User settings (parental PIN) ────────────────────────────────────
        """
        CREATE TABLE IF NOT EXISTS user_settings (
            user_id           INT PRIMARY KEY,
            parental_pin      VARCHAR(255),
            parental_enabled  BOOLEAN DEFAULT FALSE,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )""",

        // ── Commercials ────────────────────────────────────────────────────
        """
        CREATE TABLE IF NOT EXISTS commercials (
            id               INT AUTO_INCREMENT PRIMARY KEY,
            title            VARCHAR(100) NOT NULL,
            content          TEXT,
            image_url        VARCHAR(500),
            target_channel   INT,
            duration_seconds INT DEFAULT 15,
            is_active        BOOLEAN DEFAULT TRUE,
            created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )""",

        // ── Seed commercials (IGNORE if already present) ───────────────────
        """
        INSERT IGNORE INTO commercials (title, content, image_url, target_channel, duration_seconds, is_active) VALUES
        ('TechWorld Gadgets',     'Check out the latest gadgets at TechWorld! 20%% off this week.',        NULL, NULL, 15, TRUE),
        ('FreshFood Delivery',    'Order now from FreshFood – free delivery on your first order!',         NULL, NULL, 10, TRUE),
        ('SportsPulse Premium',   'Watch live sports ad-free with SportsPulse Premium subscription.',      NULL, 3,    12, TRUE),
        ('Galaxy Cinema Premiere','New blockbuster premiering this Friday on Galaxy Cinema!',             NULL, 2,    12, TRUE),
        ('KidsZone Toys',         'Get the hottest toys of the season at KidsZone Toys store!',            NULL, 5,    10, TRUE)
        """
    };

    /**
     * Run all DDL statements. Safe to call multiple times.
     */
    public static void init() {
        try (Connection c = DBConnection.getConnection();
             Statement stmt = c.createStatement()) {

            for (String sql : DDL) {
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    // Log but don't crash – some statements may fail on re-run
                    LOG.debug("DDL (ignored): {}", e.getMessage());
                }
            }
            LOG.info("Database schema initialised successfully");

        } catch (SQLException e) {
            LOG.warn("Could not initialise database schema: {}", e.getMessage());
            LOG.warn("DB features (favorites, parental control, visit history) will be disabled until MySQL is available.");
        }
    }
}
