package com.bradandmarsha.acruet.position;

import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.ledger.LedgerTransactionRepository;
import com.bradandmarsha.acruet.ledger.LedgerWriteRateLimiter;
import com.bradandmarsha.acruet.user.AcruetUser;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Household snapshot-item groups (PRODUCT.md §12).
 */
public final class PositionGroupService {

    public static final int MAX_ACTIVE_GROUPS = 20;

    private final PositionGroupStore store;
    private final LedgerWriteRateLimiter rateLimiter;

    public PositionGroupService(PositionGroupStore store) {
        this(store, null);
    }

    public PositionGroupService(PositionGroupStore store, LedgerWriteRateLimiter rateLimiter) {
        this.store = Objects.requireNonNull(store, "store");
        this.rateLimiter = rateLimiter;
    }

    public static PositionGroupService fromEnvironment() {
        return new PositionGroupService(
                new PositionGroupRepository(),
                new LedgerWriteRateLimiter(new LedgerTransactionRepository()));
    }

    public List<PositionGroup> list(AcruetUser user, boolean includeArchived) {
        requireKeySetup(user);
        return store.listByHousehold(user.householdId(), includeArchived);
    }

    public PositionGroup create(AcruetUser user, String encryptedNameBase64, Instant now) {
        requireKeySetup(user);
        validateEncryptedBlob(encryptedNameBase64);
        checkWriteRate(user.id());
        if (store.countActive(user.householdId()) >= MAX_ACTIVE_GROUPS) {
            throw new PositionException("Group limit reached (" + MAX_ACTIVE_GROUPS + ").");
        }
        PositionGroup group = new PositionGroup(
                UUID.randomUUID(),
                user.householdId(),
                PositionStatus.ACTIVE,
                EncryptedBlob.decode(encryptedNameBase64),
                now,
                now,
                null);
        store.insert(group);
        return group;
    }

    public PositionGroup rename(AcruetUser user, UUID groupId, String encryptedNameBase64) {
        requireKeySetup(user);
        validateEncryptedBlob(encryptedNameBase64);
        checkWriteRate(user.id());
        PositionGroup group = requireActive(user.householdId(), groupId);
        store.updateEncryptedName(user.householdId(), group.id(), EncryptedBlob.decode(encryptedNameBase64));
        return store.findById(user.householdId(), group.id())
                .orElseThrow(() -> new PositionException("Group not found"));
    }

    public PositionGroup archive(AcruetUser user, UUID groupId) {
        requireKeySetup(user);
        checkWriteRate(user.id());
        PositionGroup group = requireActive(user.householdId(), groupId);
        if (store.countActivePositions(user.householdId(), group.id()) > 0) {
            throw new PositionException("Move or ungroup active items before archiving this group.");
        }
        store.archive(user.householdId(), group.id());
        return store.findById(user.householdId(), group.id())
                .orElseThrow(() -> new PositionException("Group not found"));
    }

    private PositionGroup requireActive(UUID householdId, UUID groupId) {
        PositionGroup group = store.findById(householdId, groupId)
                .orElseThrow(() -> new PositionException("Group not found"));
        if (group.status() != PositionStatus.ACTIVE) {
            throw new PositionException("Group is archived.");
        }
        return group;
    }

    private static void requireKeySetup(AcruetUser user) {
        if (!user.keySetupComplete()) {
            throw new PositionException("Complete encryption key setup first.");
        }
    }

    private static void validateEncryptedBlob(String base64) {
        Optional<String> error = EncryptedBlob.validationError(base64);
        if (error.isPresent()) {
            throw new PositionException(error.get());
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
                    throw new PositionException(result.message());
                }
                rateLimiter.record(connection, userId);
            });
        } catch (PositionException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PositionException("Failed to apply write rate limit", exception);
        }
    }
}
