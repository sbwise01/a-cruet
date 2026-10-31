package com.bradandmarsha.acruet.position;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One valuation for a snapshot item. Amount is ciphertext.
 */
public record FinancialPositionSnapshot(
        UUID id,
        UUID positionId,
        LocalDate asOfDate,
        byte[] encryptedPayload,
        Instant createdAt) {
}
