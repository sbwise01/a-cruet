package com.bradandmarsha.acruet.position;

import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.household.HouseholdRepository;
import com.bradandmarsha.acruet.ledger.LedgerTransactionRepository;
import com.bradandmarsha.acruet.ledger.LedgerWriteRateLimiter;
import com.bradandmarsha.acruet.user.AcruetUser;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Snapshot asset and liability orchestration.
 */
public final class PositionService {

    public static final int MAX_ACTIVE_POSITIONS = 50;

    private final HouseholdRepository householdRepository = new HouseholdRepository();
    private final FinancialPositionRepository positionRepository = new FinancialPositionRepository();
    private final LedgerWriteRateLimiter rateLimiter =
            new LedgerWriteRateLimiter(new LedgerTransactionRepository());

    public List<FinancialPosition> listPositions(AcruetUser user, boolean includeArchived) {
        try {
            return Database.inTransactionReturning(connection ->
                    positionRepository.listByHousehold(connection, user.householdId(), includeArchived));
        } catch (SQLException exception) {
            throw new PositionException("Failed to list positions", exception);
        }
    }

    public FinancialPosition createPosition(
            AcruetUser user,
            PositionKind kind,
            String encryptedNameBase64,
            LocalDate asOfDate,
            String encryptedPayloadBase64) {
        validateEncryptedBlob(encryptedNameBase64);
        validateEncryptedBlob(encryptedPayloadBase64);
        requireAsOfDate(asOfDate);
        UUID positionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                if (positionRepository.countActive(connection, user.householdId()) >= MAX_ACTIVE_POSITIONS) {
                    throw new PositionException("Position limit reached (" + MAX_ACTIVE_POSITIONS + ").");
                }
                positionRepository.insert(
                        connection,
                        positionId,
                        user.householdId(),
                        kind,
                        EncryptedBlob.decode(encryptedNameBase64));
                positionRepository.insertSnapshot(
                        connection,
                        snapshotId,
                        positionId,
                        asOfDate,
                        EncryptedBlob.decode(encryptedPayloadBase64));
                householdRepository.incrementPositionCount(connection, user.householdId());
                rateLimiter.record(connection, user.id());
                return positionRepository.findById(connection, user.householdId(), positionId)
                        .orElseThrow(() -> new PositionException("Position not found after insert"));
            });
        } catch (PositionException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PositionException("Failed to create position", exception);
        }
    }

    public FinancialPosition updateName(AcruetUser user, UUID positionId, String encryptedNameBase64) {
        validateEncryptedBlob(encryptedNameBase64);
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                requireActive(connection, user.householdId(), positionId);
                positionRepository.updateEncryptedName(
                        connection,
                        user.householdId(),
                        positionId,
                        EncryptedBlob.decode(encryptedNameBase64));
                rateLimiter.record(connection, user.id());
                return positionRepository.findById(connection, user.householdId(), positionId)
                        .orElseThrow(() -> new PositionException("Position not found"));
            });
        } catch (PositionException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PositionException("Failed to rename position", exception);
        }
    }

    public FinancialPosition addSnapshot(
            AcruetUser user,
            UUID positionId,
            LocalDate asOfDate,
            String encryptedPayloadBase64) {
        validateEncryptedBlob(encryptedPayloadBase64);
        requireAsOfDate(asOfDate);
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                requireActive(connection, user.householdId(), positionId);
                positionRepository.insertSnapshot(
                        connection,
                        UUID.randomUUID(),
                        positionId,
                        asOfDate,
                        EncryptedBlob.decode(encryptedPayloadBase64));
                positionRepository.touch(connection, user.householdId(), positionId);
                rateLimiter.record(connection, user.id());
                return positionRepository.findById(connection, user.householdId(), positionId)
                        .orElseThrow(() -> new PositionException("Position not found"));
            });
        } catch (PositionException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PositionException("Failed to add valuation", exception);
        }
    }

    public FinancialPosition archivePosition(AcruetUser user, UUID positionId) {
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                FinancialPosition position = requireActive(connection, user.householdId(), positionId);
                positionRepository.archive(connection, user.householdId(), positionId);
                householdRepository.decrementPositionCount(connection, user.householdId());
                rateLimiter.record(connection, user.id());
                return positionRepository.findById(connection, user.householdId(), positionId)
                        .orElse(position);
            });
        } catch (PositionException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new PositionException("Failed to archive position", exception);
        }
    }

    private FinancialPosition requireActive(Connection connection, UUID householdId, UUID positionId)
            throws SQLException {
        FinancialPosition position = positionRepository.findById(connection, householdId, positionId)
                .orElseThrow(() -> new PositionException("Position not found"));
        if (position.status() != PositionStatus.ACTIVE) {
            throw new PositionException("Position is archived.");
        }
        return position;
    }

    private static void requireAsOfDate(LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new PositionException("Valuation date is required.");
        }
    }

    private static void validateEncryptedBlob(String base64) {
        Optional<String> error = EncryptedBlob.validationError(base64);
        if (error.isPresent()) {
            throw new PositionException(error.get());
        }
    }

    private void checkWriteRateLimit(Connection connection, UUID userId) throws SQLException {
        LedgerWriteRateLimiter.RateLimitResult result = rateLimiter.check(connection, userId, Instant.now());
        if (!result.permitted()) {
            throw new PositionException(result.message());
        }
    }
}
