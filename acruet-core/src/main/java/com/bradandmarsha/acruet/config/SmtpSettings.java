package com.bradandmarsha.acruet.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Proton Mail SMTP submission settings from environment / k8s Secret.
 */
public final class SmtpSettings {

    public static final String ENV_HOST = "ACRUET_SMTP_HOST";
    public static final String ENV_PORT = "ACRUET_SMTP_PORT";
    public static final String ENV_USERNAME = "ACRUET_SMTP_USERNAME";
    public static final String ENV_PASSWORD = "ACRUET_SMTP_PASSWORD";
    public static final String ENV_FROM = "ACRUET_SMTP_FROM";

    public static final String DEFAULT_HOST = "smtp.protonmail.ch";
    public static final int DEFAULT_PORT = 587;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;

    public SmtpSettings(String host, int port, String username, String password, String fromAddress) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.fromAddress = Objects.requireNonNull(fromAddress, "fromAddress");
    }

    public static SmtpSettings fromEnvironment() {
        return new SmtpSettings(
                envOrDefault(ENV_HOST, DEFAULT_HOST),
                Integer.parseInt(envOrDefault(ENV_PORT, String.valueOf(DEFAULT_PORT))),
                envOrDefault(ENV_USERNAME, ""),
                envOrDefault(ENV_PASSWORD, ""),
                envOrDefault(ENV_FROM, ""));
    }

    public boolean isConfigured() {
        return !host.isBlank()
                && port > 0
                && !username.isBlank()
                && !password.isBlank()
                && !fromAddress.isBlank();
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String fromAddress() {
        return fromAddress;
    }

    private static String envOrDefault(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }
}
