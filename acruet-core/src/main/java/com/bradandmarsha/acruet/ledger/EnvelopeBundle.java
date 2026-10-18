package com.bradandmarsha.acruet.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * Isolated envelope group inside a household. Name is ciphertext (may be unset
 * on the migrated default bundle until the client names it).
 */
public record EnvelopeBundle(
        UUID id,
        UUID householdId,
        EnvelopeBundleStatus status,
        byte[] encryptedName,
        boolean defaultBundle,
        int ledgerAccountCount,
        int ledgerAccountLimit,
        int transactionCount,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {
}
