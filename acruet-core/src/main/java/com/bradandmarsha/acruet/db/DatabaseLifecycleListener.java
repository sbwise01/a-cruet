package com.bradandmarsha.acruet.db;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Runs Flyway migrations on WAR startup.
 */
@WebListener
public class DatabaseLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        FlywayMigrator.migrate();
    }
}
