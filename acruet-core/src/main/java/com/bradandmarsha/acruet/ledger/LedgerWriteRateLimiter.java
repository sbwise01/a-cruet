package com.bradandmarsha.acruet.ledger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Per-user ledger write throttling (PRODUCT.md #50, Phase 8).
 */
public final class LedgerWriteRateLimiter {

    public static final int MAX_WRITES_PER_MINUTE = 30;
    public static final int MAX_WRITES_PER_HOUR = 200;

    private final LedgerTransactionRepository repository;

    public LedgerWriteRateLimiter(LedgerTransactionRepository repository) {
        this.repository = repository;
    }

    public RateLimitResult check(Connection connection, UUID userId, Instant now) throws SQLException {
        int minuteAttempts = repository.countWriteAttempts(connection, userId, now.minus(1, ChronoUnit.MINUTES));
        if (minuteAttempts >= MAX_WRITES_PER_MINUTE) {
            return RateLimitResult.denied("Too many ledger writes. Wait a moment and try again.");
        }
        int hourAttempts = repository.countWriteAttempts(connection, userId, now.minus(1, ChronoUnit.HOURS));
        if (hourAttempts >= MAX_WRITES_PER_HOUR) {
            return RateLimitResult.denied("Too many ledger writes this hour. Try again later.");
        }
        return RateLimitResult.allowed();
    }

    public void record(Connection connection, UUID userId) throws SQLException {
        repository.recordWriteAttempt(connection, userId);
    }

    public record RateLimitResult(boolean permitted, String message) {
        static RateLimitResult allowed() {
            return new RateLimitResult(true, "");
        }

        static RateLimitResult denied(String message) {
            return new RateLimitResult(false, message);
        }
    }
}
