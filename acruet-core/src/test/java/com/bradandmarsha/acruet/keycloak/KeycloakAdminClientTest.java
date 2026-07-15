package com.bradandmarsha.acruet.keycloak;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeycloakAdminClientTest {

    @Test
    void splitNameUsesFirstTokenAndRemainder() {
        KeycloakAdminClient.NameParts parts = KeycloakAdminClient.splitName("Stephen Wise");
        assertEquals("Stephen", parts.firstName());
        assertEquals("Wise", parts.lastName());
    }

    @Test
    void splitNameFallsBackForSingleToken() {
        KeycloakAdminClient.NameParts parts = KeycloakAdminClient.splitName("Madonna");
        assertEquals("Madonna", parts.firstName());
        assertEquals("User", parts.lastName());
    }

    @Test
    void temporaryPasswordHasExpectedLength() {
        String password = KeycloakAdminClient.newTemporaryPassword();
        assertEquals(16, password.length());
    }
}
