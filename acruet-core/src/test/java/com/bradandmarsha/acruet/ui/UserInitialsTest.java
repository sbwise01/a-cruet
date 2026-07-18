package com.bradandmarsha.acruet.ui;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.user.AcruetUser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserInitialsTest {

    @Test
    void initialsFromDisplayName() {
        AcruetUser user = new AcruetUser(
                java.util.UUID.randomUUID(),
                "kc-1",
                "alice@example.com",
                "Alice Example",
                null,
                0,
                0,
                100,
                true,
                java.time.Instant.now(),
                java.time.Instant.now(),
                null,
                null);
        OidcUser oidc = new OidcUser("kc-1", "alice", "alice@example.com", null, null, Set.of());
        assertEquals("AE", UserInitials.from(user, oidc));
    }

    @Test
    void initialsFromGivenFamilyName() {
        OidcUser oidc = new OidcUser(
                "kc-2", "swise", "sbwise@gmail.com", "Stephen", "Wise", Set.of());
        assertEquals("SW", UserInitials.from(oidc));
        assertEquals("Stephen Wise", UserInitials.displayName(null, oidc));
    }

    @Test
    void initialsFallbackToEmail() {
        OidcUser oidc = new OidcUser("kc-3", "solo", "solo@example.com", null, null, Set.of());
        assertEquals("SO", UserInitials.from(oidc));
    }
}
