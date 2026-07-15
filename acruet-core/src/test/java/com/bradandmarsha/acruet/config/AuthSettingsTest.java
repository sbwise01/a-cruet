package com.bradandmarsha.acruet.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthSettingsTest {

    @Test
    void issuerBuildsHttpsRealmUrl() {
        AuthSettings settings = new AuthSettings("auth.example.com", "wise-k8s");
        assertEquals("https://auth.example.com/realms/wise-k8s", settings.issuer());
    }
}
