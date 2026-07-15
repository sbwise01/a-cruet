package com.bradandmarsha.acruet.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpSettingsTest {

    @Test
    void defaultsMatchProtonSubmission() {
        SmtpSettings settings = new SmtpSettings("smtp.protonmail.ch", 587, "user", "pass", "noreply@example.com");
        assertEquals("smtp.protonmail.ch", settings.host());
        assertEquals(587, settings.port());
    }

    @Test
    void isConfiguredRequiresCredentialsAndFrom() {
        SmtpSettings incomplete = new SmtpSettings("smtp.protonmail.ch", 587, "", "pass", "noreply@example.com");
        assertFalse(incomplete.isConfigured());

        SmtpSettings complete = new SmtpSettings("smtp.protonmail.ch", 587, "user", "pass", "noreply@example.com");
        assertTrue(complete.isConfigured());
    }
}
