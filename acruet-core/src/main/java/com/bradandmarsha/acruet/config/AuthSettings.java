package com.bradandmarsha.acruet.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Keycloak auth hostname for OIDC issuer and Admin API URLs.
 */
public final class AuthSettings {

    public static final String ENV_AUTH_HOST = "ACRUET_AUTH_HOST";
    public static final String DEFAULT_AUTH_HOST = "auth.home.bradandmarsha.com";
    public static final String DEFAULT_REALM = "wise-k8s";

    private final String authHost;
    private final String realm;

    public AuthSettings(String authHost, String realm) {
        this.authHost = Objects.requireNonNull(authHost, "authHost");
        this.realm = Objects.requireNonNull(realm, "realm");
    }

    public static AuthSettings fromEnvironment() {
        return new AuthSettings(
                envOrDefault(ENV_AUTH_HOST, DEFAULT_AUTH_HOST),
                DEFAULT_REALM);
    }

    public String issuer() {
        return "https://" + authHost + "/realms/" + realm;
    }

    public String authHost() {
        return authHost;
    }

    public String realm() {
        return realm;
    }

    private static String envOrDefault(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }
}
