package com.bradandmarsha.acruet.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Homelab media site hostname for index-tile images in HTML pages.
 */
public final class MediaSettings {

    public static final String ENV_HOST = "ACRUET_MEDIA_HOST";
    public static final String DEFAULT_HOST = "media.home.bradandmarsha.com";

    private final String host;

    public MediaSettings(String host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public static MediaSettings fromEnvironment() {
        return new MediaSettings(envOrDefault(ENV_HOST, DEFAULT_HOST));
    }

    /**
     * Absolute HTTPS URL for a tile image under {@code /media/} on the media host.
     */
    public String tileImageUrl(String mediaPath) {
        String path = mediaPath.startsWith("/") ? mediaPath : "/media/" + mediaPath;
        return "https://" + host + path;
    }

    public String host() {
        return host;
    }

    private static String envOrDefault(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }
}
