package com.bradandmarsha.acruet.config;

import java.util.Objects;
import java.util.Optional;

/**
 * JDBC connection settings sourced from environment variables.
 *
 * <p>Designed to align with CNPG-generated Secret keys injected into the
 * deployment later ({@code host}, {@code port}, {@code dbname}, {@code username},
 * {@code password})</p>
 */
public final class DatabaseSettings {

    public static final String ENV_HOST = "ACRUET_DB_HOST";
    public static final String ENV_PORT = "ACRUET_DB_PORT";
    public static final String ENV_NAME = "ACRUET_DB_NAME";
    public static final String ENV_USERNAME = "ACRUET_DB_USERNAME";
    public static final String ENV_PASSWORD = "ACRUET_DB_PASSWORD";

    private final String host;
    private final int port;
    private final String databaseName;
    private final String username;
    private final String password;

    public DatabaseSettings(String host, int port, String databaseName, String username, String password) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.databaseName = Objects.requireNonNull(databaseName, "databaseName");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    public static DatabaseSettings fromEnvironment() {
        return new DatabaseSettings(
                envOrDefault(ENV_HOST, "localhost"),
                Integer.parseInt(envOrDefault(ENV_PORT, "5432")),
                envOrDefault(ENV_NAME, "acruet"),
                envOrDefault(ENV_USERNAME, "acruet"),
                envOrDefault(ENV_PASSWORD, "")
        );
    }

    public boolean isConfigured() {
        return !host.isBlank()
                && !databaseName.isBlank()
                && !username.isBlank()
                && !password.isBlank();
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String databaseName() {
        return databaseName;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    }

    private static String envOrDefault(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }
}
