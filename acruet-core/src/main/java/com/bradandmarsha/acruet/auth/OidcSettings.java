package com.bradandmarsha.acruet.auth;

import java.util.Objects;
import java.util.Optional;

/**
 * OpenID Connect client settings for Tomcat server-side sessions.
 */
public final class OidcSettings {

    public static final String ENV_CLIENT_ID = "ACRUET_OIDC_CLIENT_ID";
    public static final String ENV_CLIENT_SECRET = "ACRUET_OIDC_CLIENT_SECRET";
    public static final String ENV_ISSUER = "ACRUET_OIDC_ISSUER";
    public static final String ENV_BASE_URL = "ACRUET_BASE_URL";
    public static final String ENV_ADMIN_ROLE = "ACRUET_ADMIN_ROLE";
    public static final String ENV_REQUIRE_ADMIN_ROLE = "ACRUET_REQUIRE_ADMIN_ROLE";

    public static final String DEFAULT_CLIENT_ID = "acruet";
    public static final String DEFAULT_ISSUER = "https://auth.home.bradandmarsha.com/realms/wise-k8s";
    public static final String DEFAULT_ADMIN_ROLE = "a-cruet-admin";
    public static final String CALLBACK_PATH = "/auth/callback";
    public static final String LOGOUT_PATH = "/auth/logout";

    private final String clientId;
    private final String clientSecret;
    private final String issuer;
    private final String baseUrl;
    private final String adminRole;
    private final boolean requireAdminRole;

    public OidcSettings(
            String clientId,
            String clientSecret,
            String issuer,
            String baseUrl,
            String adminRole,
            boolean requireAdminRole) {
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.clientSecret = Objects.requireNonNull(clientSecret, "clientSecret");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.adminRole = Objects.requireNonNull(adminRole, "adminRole");
        this.requireAdminRole = requireAdminRole;
    }

    public static OidcSettings fromEnvironment() {
        return new OidcSettings(
                envOrDefault(ENV_CLIENT_ID, DEFAULT_CLIENT_ID),
                envOrDefault(ENV_CLIENT_SECRET, ""),
                trimTrailingSlash(envOrDefault(ENV_ISSUER, DEFAULT_ISSUER)),
                trimTrailingSlash(envOrDefault(ENV_BASE_URL, "")),
                envOrDefault(ENV_ADMIN_ROLE, DEFAULT_ADMIN_ROLE),
                Boolean.parseBoolean(envOrDefault(ENV_REQUIRE_ADMIN_ROLE, "false")));
    }

    public boolean isConfigured() {
        return !clientId.isBlank()
                && !clientSecret.isBlank()
                && !issuer.isBlank()
                && !baseUrl.isBlank();
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

    public String baseUrl() {
        return baseUrl;
    }

    public String adminRole() {
        return adminRole;
    }

    public boolean requireAdminRole() {
        return requireAdminRole;
    }

    public String callbackUrl() {
        return baseUrl + CALLBACK_PATH;
    }

    public String discoveryUrl() {
        return issuer + "/.well-known/openid-configuration";
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
