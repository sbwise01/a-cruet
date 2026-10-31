package com.bradandmarsha.acruet.position;

import java.time.Instant;
import java.util.UUID;

/**
 * Household snapshot asset or liability. Name is ciphertext.
 */
public record FinancialPosition(
        UUID id,
        UUID householdId,
        PositionKind kind,
        PositionStatus status,
        byte[] encryptedName,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        FinancialPositionSnapshot latestSnapshot) {
}
