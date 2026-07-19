package com.bradandmarsha.acruet.config;

import java.util.Optional;

/**
 * Shared secret for in-cluster CronJob callbacks to admin internal endpoints (Phase 11).
 */
public final class CronSettings {

    public static final String ENV_SECRET = "ACRUET_CRON_SECRET";

    private CronSettings() {
    }

    public static Optional<String> secretFromEnvironment() {
        return Optional.ofNullable(System.getenv(ENV_SECRET)).filter(value -> !value.isBlank());
    }

    public static boolean matches(String providedSecret) {
        Optional<String> configured = secretFromEnvironment();
        if (configured.isEmpty()) {
            return false;
        }
        if (providedSecret == null || providedSecret.isBlank()) {
            return false;
        }
        return configured.get().equals(providedSecret);
    }
}
