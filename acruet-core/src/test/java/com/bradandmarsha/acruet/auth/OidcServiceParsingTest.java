package com.bradandmarsha.acruet.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcServiceParsingTest {

    @Test
    void userFromAccessTokenReadsRealmRoles() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-123")
                .claim("preferred_username", "alice")
                .claim("email", "alice@example.com")
                .claim("given_name", "Alice")
                .claim("family_name", "Example")
                .claim("realm_access", java.util.Map.of("roles", List.of("a-cruet-admin", "offline_access")))
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

        String token = new PlainJWT(claims).serialize();
        OidcUser user = OidcService.userFromAccessToken(token);

        assertEquals("user-123", user.subject());
        assertEquals("alice", user.preferredUsername());
        assertEquals("alice@example.com", user.email());
        assertEquals("Alice", user.givenName());
        assertEquals("Example", user.familyName());
        assertEquals(Set.of("a-cruet-admin", "offline_access"), user.realmRoles());
        assertTrue(user.hasRealmRole("a-cruet-admin"));
    }
}
