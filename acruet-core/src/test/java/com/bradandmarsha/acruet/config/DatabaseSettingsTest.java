package com.bradandmarsha.acruet.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSettingsTest {

    @Test
    void jdbcUrlUsesHostPortAndDatabase() {
        DatabaseSettings settings = new DatabaseSettings(
                "acruet-db-rw.acruet-cnpg.svc",
                5432,
                "acruet",
                "acruet",
                "secret"
        );

        assertEquals(
                "jdbc:postgresql://acruet-db-rw.acruet-cnpg.svc:5432/acruet",
                settings.jdbcUrl()
        );
    }

    @Test
    void isConfiguredRequiresPassword() {
        DatabaseSettings missingPassword = new DatabaseSettings("localhost", 5432, "acruet", "acruet", "");
        assertFalse(missingPassword.isConfigured());

        DatabaseSettings complete = new DatabaseSettings("localhost", 5432, "acruet", "acruet", "secret");
        assertTrue(complete.isConfigured());
    }
}
