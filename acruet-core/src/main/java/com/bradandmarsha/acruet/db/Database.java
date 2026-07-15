package com.bradandmarsha.acruet.db;

import com.bradandmarsha.acruet.config.DatabaseSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Simple JDBC connections for homelab-scale request handling.
 */
public final class Database {

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("PostgreSQL JDBC driver not available", exception);
        }
    }

    private Database() {
    }

    public static Connection openConnection() throws SQLException {
        DatabaseSettings settings = DatabaseSettings.fromEnvironment();
        if (!settings.isConfigured()) {
            throw new SQLException("Database is not configured");
        }
        return DriverManager.getConnection(
                settings.jdbcUrl(), settings.username(), settings.password());
    }
}
