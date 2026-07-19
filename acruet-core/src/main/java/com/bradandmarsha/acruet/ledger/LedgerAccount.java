package com.bradandmarsha.acruet.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * Ledger envelope account — encrypted name stored server-side as ciphertext.
 */
public record LedgerAccount(
        UUID id,
        UUID householdId,
        LedgerAccountStatus status,
        byte[] encryptedName,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {
}
