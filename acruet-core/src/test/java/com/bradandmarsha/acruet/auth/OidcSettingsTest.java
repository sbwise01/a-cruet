package com.bradandmarsha.acruet.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcSettingsTest {

    @Test
    void configuredWhenRequiredValuesPresent() {
        OidcSettings settings = new OidcSettings(
                "acruet",
                "secret",
                "https://auth.example.com/realms/wise-k8s",
                "https://acruet.example.com",
                "a-cruet-admin",
                false);
        assertTrue(settings.isConfigured());
        assertEquals("https://acruet.example.com/auth/callback", settings.callbackUrl());
    }

    @Test
    void notConfiguredWithoutClientSecret() {
        OidcSettings settings = new OidcSettings(
                "acruet",
                "",
                "https://auth.example.com/realms/wise-k8s",
                "https://acruet.example.com",
                "a-cruet-admin",
                true);
        assertFalse(settings.isConfigured());
        assertTrue(settings.requireAdminRole());
    }
}
