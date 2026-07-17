package com.bradandmarsha.acruet.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Append-only ledger transaction with encrypted payload (memo, amounts, lines).
 */
public record LedgerTransaction(
        UUID id,
        UUID userId,
        TransactionType transactionType,
        LocalDate transactionDate,
        byte[] encryptedPayload,
        Instant createdAt,
        List<UUID> accountIds) {
}
