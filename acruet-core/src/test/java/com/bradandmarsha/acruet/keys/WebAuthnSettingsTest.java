package com.bradandmarsha.acruet.keys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebAuthnSettingsTest {

    @Test
    void rpIdIsUserHostname() {
        WebAuthnSettings settings = WebAuthnSettings.fromBaseUrl("https://acruet.home.bradandmarsha.com/");
        assertEquals("acruet.home.bradandmarsha.com", settings.rpId());
        assertEquals("https://acruet.home.bradandmarsha.com", settings.origin());
    }
}
