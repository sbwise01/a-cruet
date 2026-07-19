package com.bradandmarsha.acruet.household;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared ledger household — operational counts and envelope limit (Phase 12).
 */
public record Household(
        UUID id,
        int ledgerAccountCount,
        int transactionCount,
        int ledgerAccountLimit,
        Instant createdAt,
        Instant updatedAt) {
}
