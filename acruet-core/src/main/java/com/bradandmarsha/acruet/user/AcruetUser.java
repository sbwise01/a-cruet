package com.bradandmarsha.acruet.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Provisioned a-cruet user row (Phase 6+) including key-setup gate (Phase 7).
 */
public record AcruetUser(
        UUID id,
        String keycloakUserId,
        String email,
        String displayName,
        UUID signupApplicationId,
        String phone,
        String mailingAddress,
        boolean allowNegativeWithdraw,
        UUID householdId,
        int ledgerAccountCount,
        int transactionCount,
        int ledgerAccountLimit,
        boolean keySetupComplete,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        Instant lastTransactionAt) {
}
