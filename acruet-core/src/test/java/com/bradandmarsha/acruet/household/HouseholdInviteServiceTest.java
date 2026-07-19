package com.bradandmarsha.acruet.household;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseholdInviteServiceTest {

    @Test
    void rejectsInvalidInviteSaltLength() {
        HouseholdInviteService.InviteRequest request = validInviteRequest(
                "invitee@example.com",
                Base64.getEncoder().encodeToString(new byte[4]));
        HouseholdInviteService service = HouseholdInviteService.fromEnvironment();
        HouseholdInviteService.CreateResult result =
                service.createInvite(sampleUser(), request, java.time.Instant.now());
        assertTrue(result.message().contains("Invalid invite encryption metadata"));
    }

    @Test
    void rejectsBlankEmailInInviteRequest() {
        HouseholdInviteService.InviteRequest request = validInviteRequest(" ", null);
        HouseholdInviteService service = HouseholdInviteService.fromEnvironment();
        HouseholdInviteService.CreateResult result =
                service.createInvite(sampleUser(), request, java.time.Instant.now());
        assertTrue(result.message().contains("valid email"));
    }

    @Test
    void maxHouseholdMembersIsFive() {
        assertEquals(5, HouseholdInviteService.MAX_HOUSEHOLD_MEMBERS);
    }

    private static HouseholdInviteService.InviteRequest validInviteRequest(String email, String saltBase64) {
        return new HouseholdInviteService.InviteRequest(
                email,
                "invite-token-value",
                Base64.getEncoder().encodeToString(new byte[40]),
                "AES-KW",
                "PBKDF2",
                "SHA-256",
                saltBase64 == null ? Base64.getEncoder().encodeToString(new byte[16]) : saltBase64,
                0);
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
