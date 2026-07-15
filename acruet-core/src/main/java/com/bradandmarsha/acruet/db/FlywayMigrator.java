package com.bradandmarsha.acruet.db;

import com.bradandmarsha.acruet.config.DatabaseSettings;
import org.flywaydb.core.Flyway;

/**
 * Applies classpath Flyway migrations when the database is configured.
 */
public final class FlywayMigrator {

    private FlywayMigrator() {
    }

    public static void migrate() {
        DatabaseSettings settings = DatabaseSettings.fromEnvironment();
        if (!settings.isConfigured()) {
            return;
        }
        Flyway.configure()
                .dataSource(settings.jdbcUrl(), settings.username(), settings.password())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
