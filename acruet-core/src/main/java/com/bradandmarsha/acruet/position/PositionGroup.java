package com.bradandmarsha.acruet.position;

import java.time.Instant;
import java.util.UUID;

/**
 * Household label for snapshot items. Name is ciphertext.
 */
public record PositionGroup(
        UUID id,
        UUID householdId,
        PositionStatus status,
        byte[] encryptedName,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {
}
