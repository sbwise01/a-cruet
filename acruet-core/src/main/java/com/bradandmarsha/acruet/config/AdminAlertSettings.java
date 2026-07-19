package com.bradandmarsha.acruet.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Comma-separated administrator inboxes for operational alerts (Phase 11).
 */
public final class AdminAlertSettings {

    public static final String ENV_ALERT_EMAILS = "ACRUET_ADMIN_ALERT_EMAILS";

    private AdminAlertSettings() {
    }

    public static List<String> alertEmailsFromEnvironment() {
        return Optional.ofNullable(System.getenv(ENV_ALERT_EMAILS))
                .filter(value -> !value.isBlank())
                .stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(email -> !email.isBlank())
                .toList();
    }
}
