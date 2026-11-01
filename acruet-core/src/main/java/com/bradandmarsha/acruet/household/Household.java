package com.bradandmarsha.acruet.household;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared ledger household — operational counts, envelope limit, and snapshot items.
 */
public record Household(
        UUID id,
        int ledgerAccountCount,
        int transactionCount,
        int ledgerAccountLimit,
        int bundleCount,
        int positionCount,
        int netWorthSnapshotCount,
        Instant createdAt,
        Instant updatedAt) {
}
