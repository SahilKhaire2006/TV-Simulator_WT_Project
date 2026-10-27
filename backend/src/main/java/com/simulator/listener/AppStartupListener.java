package com.simulator.listener;

import com.simulator.dao.UserDAO;
import com.simulator.db.DBConnection;
import com.simulator.db.SchemaInitializer;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs when the web application starts.  Responsible for:
 * <ol>
 *   <li>Initialising the database schema (auto-create tables)</li>
 *   <li>Seeding a default admin account</li>
 * </ol>
 */
@WebListener
public class AppStartupListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppStartupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("── TV Broadcast Simulator starting up ──");

        if (DBConnection.isAvailable()) {
            LOG.info("Database is available – initialising schema...");
            SchemaInitializer.init();

            UserDAO userDAO = new UserDAO();
            userDAO.seedAdminIfAbsent();
            LOG.info("Database ready: tables created, admin seeded.");
        } else {
            LOG.warn("Database not available. Running in degraded mode.");
            LOG.warn("To enable favorites, parental control, and visit history:");
            LOG.warn("  1. Install MySQL 8+");
            LOG.warn("  2. Edit backend/src/main/resources/db.properties");
            LOG.warn("  3. Restart the server");
        }

        LOG.info("── Startup complete ──");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOG.info("TV Broadcast Simulator shutting down");
    }
}
