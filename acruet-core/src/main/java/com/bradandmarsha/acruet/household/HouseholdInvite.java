package com.bradandmarsha.acruet.household;

import java.time.Instant;
import java.util.UUID;

/**
 * Stored household invite with ciphertext DEK wrap (Phase 12c).
 */
public record HouseholdInvite(
        UUID id,
        UUID householdId,
        String email,
        UUID invitedByUserId,
        byte[] encryptedInvitePayload,
        String wrapAlgorithm,
        String kdfAlgorithm,
        String kdfHash,
        byte[] kdfSalt,
        int kdfIterations,
        HouseholdInviteStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {
}
