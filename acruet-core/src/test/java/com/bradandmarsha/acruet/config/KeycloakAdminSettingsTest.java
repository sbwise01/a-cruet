package com.bradandmarsha.acruet.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakAdminSettingsTest {

    @Test
    void derivesAdminApiBaseUrlFromIssuer() {
        KeycloakAdminSettings settings = new KeycloakAdminSettings(
                "acruet-admin",
                "secret",
                "https://auth.example.com/realms/wise-k8s");
        assertEquals("wise-k8s", settings.realm());
        assertEquals(
                "https://auth.example.com/admin/realms/wise-k8s",
                settings.adminApiBaseUrl());
        assertEquals(
                "https://auth.example.com/realms/wise-k8s/protocol/openid-connect/token",
                settings.tokenEndpoint());
    }

    @Test
    void configuredWhenClientSecretPresent() {
        KeycloakAdminSettings settings = new KeycloakAdminSettings(
                "acruet-admin", "secret", "https://auth.example.com/realms/wise-k8s");
        assertTrue(settings.isConfigured());

        KeycloakAdminSettings incomplete = new KeycloakAdminSettings(
                "acruet-admin", "", "https://auth.example.com/realms/wise-k8s");
        assertFalse(incomplete.isConfigured());
    }
}
