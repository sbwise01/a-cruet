package com.bradandmarsha.acruet.household;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdInviteServiceTest {

    @Test
    void rejectsBlankEmailInInviteRequest() {
        HouseholdInviteService.InviteRequest request = new HouseholdInviteService.InviteRequest(
                " ",
                "token",
                "d2FybmE=",
                "AES-KW",
                "PBKDF2",
                "SHA-256",
                "c2FsdA==",
                0);
        HouseholdInviteService service = HouseholdInviteService.fromEnvironment();
        HouseholdInviteService.CreateResult result =
                service.createInvite(sampleUser(), request, java.time.Instant.now());
        assertTrue(result.message().contains("valid email"));
    }

    @Test
    void maxHouseholdMembersIsFive() {
        assertEquals(5, HouseholdInviteService.MAX_HOUSEHOLD_MEMBERS);
    }

    private static com.bradandmarsha.acruet.user.AcruetUser sampleUser() {
        java.time.Instant now = java.time.Instant.now();
        return new com.bradandmarsha.acruet.user.AcruetUser(
                java.util.UUID.randomUUID(),
                "kc-1",
                "owner@example.com",
                "Owner Example",
                null,
                "555-0100",
                "1 Main St",
                false,
                java.util.UUID.randomUUID(),
                0,
                0,
                100,
                true,
                now,
                now,
                now,
                now);
    }
}
