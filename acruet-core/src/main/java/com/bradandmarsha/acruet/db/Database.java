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

    public static void inTransaction(TransactionWork work) throws SQLException {
        try (Connection connection = openConnection()) {
            runInTransaction(connection, conn -> {
                work.run(conn);
                return null;
            });
        }
    }

    public static <T> T inTransactionReturning(TransactionReturningWork<T> work) throws SQLException {
        try (Connection connection = openConnection()) {
            return runInTransaction(connection, work::run);
        }
    }

    private static <T> T runInTransaction(Connection connection, SqlReturningWork<T> work) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } catch (RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @FunctionalInterface
    public interface TransactionWork {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionReturningWork<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlReturningWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
