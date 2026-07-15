package com.bradandmarsha.acruet.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Keycloak Admin API client-credentials settings for the {@code acruet-admin} service account.
 */
public final class KeycloakAdminSettings {

    public static final String ENV_CLIENT_ID = "ACRUET_KEYCLOAK_ADMIN_CLIENT_ID";
    public static final String ENV_CLIENT_SECRET = "ACRUET_KEYCLOAK_ADMIN_CLIENT_SECRET";

    public static final String DEFAULT_CLIENT_ID = "acruet-admin";

    private final String clientId;
    private final String clientSecret;
    private final String issuer;

    public KeycloakAdminSettings(String clientId, String clientSecret, String issuer) {
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
        this.issuer = trimTrailingSlash(Objects.requireNonNull(issuer, "issuer"));
    }

    public static KeycloakAdminSettings fromEnvironment() {
        return new KeycloakAdminSettings(
                envOrDefault(ENV_CLIENT_ID, DEFAULT_CLIENT_ID),
                envOrDefault(ENV_CLIENT_SECRET, ""),
                AuthSettings.fromEnvironment().issuer());
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !issuer.isBlank();
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String issuer() {
        return issuer;
    }

    public String realm() {
        int realmsIndex = issuer.indexOf("/realms/");
        if (realmsIndex < 0) {
            throw new IllegalStateException("Issuer must include /realms/: " + issuer);
        }
        return issuer.substring(realmsIndex + "/realms/".length());
    }

    public String tokenEndpoint() {
        return issuer + "/protocol/openid-connect/token";
    }

    public String adminApiBaseUrl() {
        int realmsIndex = issuer.indexOf("/realms/");
        if (realmsIndex < 0) {
            throw new IllegalStateException("Issuer must include /realms/: " + issuer);
        }
        return issuer.substring(0, realmsIndex) + "/admin/realms/" + realm();
    }

    private static String envOrDefault(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
