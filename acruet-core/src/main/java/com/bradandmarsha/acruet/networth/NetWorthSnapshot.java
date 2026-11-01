package com.bradandmarsha.acruet.networth;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One household net-worth observation. Amount is ciphertext.
 */
public record NetWorthSnapshot(
        UUID id,
        UUID householdId,
        LocalDate asOfDate,
        byte[] encryptedPayload,
        Instant createdAt) {
}
