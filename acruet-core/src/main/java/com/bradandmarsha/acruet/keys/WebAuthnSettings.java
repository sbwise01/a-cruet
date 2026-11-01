package com.bradandmarsha.acruet.keys;

import com.bradandmarsha.acruet.auth.OidcSettings;

import java.net.URI;
import java.util.Objects;

/**
 * Relying-party identity for user-app WebAuthn (origin / RP id = user hostname).
 */
public final class WebAuthnSettings {

    public static final String DEFAULT_RP_NAME = "a-cruet";
    public static final String DEFAULT_ORIGIN = "https://acruet.home.bradandmarsha.com";
    public static final byte[] PRF_EVAL_FIRST = "acruet-device-wrap-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private final String rpId;
    private final String origin;
    private final String rpName;

    public WebAuthnSettings(String rpId, String origin, String rpName) {
        this.rpId = Objects.requireNonNull(rpId, "rpId");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.rpName = Objects.requireNonNull(rpName, "rpName");
    }

    public static WebAuthnSettings fromEnvironment() {
        return fromBaseUrl(OidcSettings.fromEnvironment().baseUrl());
    }

    public static WebAuthnSettings fromBaseUrl(String baseUrl) {
        String origin = normalizeOrigin(baseUrl);
        return new WebAuthnSettings(hostOf(origin), origin, DEFAULT_RP_NAME);
    }

    public String rpId() {
        return rpId;
    }

    public String origin() {
        return origin;
    }

    public String rpName() {
        return rpName;
    }

    private static String normalizeOrigin(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_ORIGIN;
        }
        URI uri = URI.create(baseUrl.strip());
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new DeviceUnlockException("User base URL is not a valid origin.");
        }
        StringBuilder origin = new StringBuilder()
                .append(uri.getScheme())
                .append("://")
                .append(uri.getHost());
        if (uri.getPort() > 0) {
            origin.append(':').append(uri.getPort());
        }
        return origin.toString();
    }

    static String hostOf(String origin) {
        return URI.create(origin).getHost();
    }
}
