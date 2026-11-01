package com.bradandmarsha.acruet.networth;

import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.ledger.LedgerTransactionRepository;
import com.bradandmarsha.acruet.ledger.LedgerWriteRateLimiter;
import com.bradandmarsha.acruet.user.AcruetUser;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Household net-worth register (PRODUCT.md §11).
 */
public final class NetWorthService {

    public static final int MAX_SNAPSHOTS = 600;

    private final NetWorthStore store;
    private final LedgerWriteRateLimiter rateLimiter;

    public NetWorthService(NetWorthStore store) {
        this(store, null);
    }

    public NetWorthService(NetWorthStore store, LedgerWriteRateLimiter rateLimiter) {
        this.store = Objects.requireNonNull(store, "store");
        this.rateLimiter = rateLimiter;
    }

    public static NetWorthService fromEnvironment() {
        return new NetWorthService(
                new NetWorthSnapshotRepository(),
                new LedgerWriteRateLimiter(new LedgerTransactionRepository()));
    }

    public List<NetWorthSnapshot> list(AcruetUser user) {
        requireKeySetup(user);
        return store.listByHousehold(user.householdId());
    }

    public Optional<NetWorthSnapshot> latest(AcruetUser user) {
        requireKeySetup(user);
        return store.findLatest(user.householdId());
    }

    public int count(AcruetUser user) {
        requireKeySetup(user);
        return store.countByHousehold(user.householdId());
    }

    public NetWorthSnapshot create(AcruetUser user, LocalDate asOfDate, String encryptedPayloadBase64, Instant now) {
        requireKeySetup(user);
        requireAsOfDate(asOfDate);
        validateEncryptedBlob(encryptedPayloadBase64);
        checkWriteRate(user.id());
        if (store.countByHousehold(user.householdId()) >= MAX_SNAPSHOTS) {
            throw new NetWorthException("Net worth limit reached (" + MAX_SNAPSHOTS + ").");
        }
        NetWorthSnapshot snapshot = new NetWorthSnapshot(
                UUID.randomUUID(),
                user.householdId(),
                asOfDate,
                EncryptedBlob.decode(encryptedPayloadBase64),
                now);
        store.insert(snapshot);
        return snapshot;
    }

    private static void requireKeySetup(AcruetUser user) {
        if (!user.keySetupComplete()) {
            throw new NetWorthException("Complete encryption key setup first.");
        }
    }

    private static void requireAsOfDate(LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new NetWorthException("As-of date is required.");
        }
    }

    private static void validateEncryptedBlob(String base64) {
        Optional<String> error = EncryptedBlob.validationError(base64);
        if (error.isPresent()) {
            throw new NetWorthException(error.get());
        }
    }

    private void checkWriteRate(UUID userId) {
        if (rateLimiter == null) {
            return;
        }
        try {
            Database.inTransaction(connection -> {
                LedgerWriteRateLimiter.RateLimitResult result = rateLimiter.check(connection, userId, Instant.now());
                if (!result.permitted()) {
                    throw new NetWorthException(result.message());
                }
                rateLimiter.record(connection, userId);
            });
        } catch (NetWorthException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new NetWorthException("Failed to apply write rate limit", exception);
        }
    }
}
