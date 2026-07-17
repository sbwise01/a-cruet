package com.bradandmarsha.acruet.ledger;

import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.user.AcruetUser;
import com.bradandmarsha.acruet.user.UserRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ledger account and transaction orchestration (Phase 8).
 */
public final class LedgerService {

    private final UserRepository userRepository = new UserRepository();
    private final LedgerAccountRepository accountRepository = new LedgerAccountRepository();
    private final LedgerTransactionRepository transactionRepository = new LedgerTransactionRepository();
    private final LedgerWriteRateLimiter rateLimiter = new LedgerWriteRateLimiter(transactionRepository);

    public List<LedgerAccount> listAccounts(AcruetUser user, boolean includeArchived) {
        try {
            return Database.inTransactionReturning(connection ->
                    accountRepository.listByUser(connection, user.id(), includeArchived));
        } catch (SQLException exception) {
            throw new LedgerException("Failed to list accounts", exception);
        }
    }

    public LedgerAccount createAccount(AcruetUser user, String encryptedNameBase64) {
        validateEncryptedBlob(encryptedNameBase64);
        UUID accountId = UUID.randomUUID();
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                AcruetUser current = reloadUser(connection, user.id());
                if (current.ledgerAccountCount() >= current.ledgerAccountLimit()) {
                    throw new LedgerException("Account limit reached (" + current.ledgerAccountLimit() + ").");
                }
                byte[] encryptedName = EncryptedBlob.decode(encryptedNameBase64);
                accountRepository.insert(connection, accountId, user.id(), encryptedName);
                userRepository.incrementLedgerAccountCount(connection, user.id());
                rateLimiter.record(connection, user.id());
                return accountRepository.findById(connection, user.id(), accountId)
                        .orElseThrow(() -> new LedgerException("Account not found after insert"));
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to create account", exception);
        }
    }

    public LedgerAccount updateAccountName(AcruetUser user, UUID accountId, String encryptedNameBase64) {
        validateEncryptedBlob(encryptedNameBase64);
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                byte[] encryptedName = EncryptedBlob.decode(encryptedNameBase64);
                accountRepository.updateEncryptedName(connection, user.id(), accountId, encryptedName);
                rateLimiter.record(connection, user.id());
                return accountRepository.findById(connection, user.id(), accountId)
                        .orElseThrow(() -> new LedgerException("Account not found"));
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to update account", exception);
        }
    }

    public LedgerAccount archiveAccount(AcruetUser user, UUID accountId) {
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                LedgerAccount account = accountRepository.findById(connection, user.id(), accountId)
                        .orElseThrow(() -> new LedgerException("Account not found"));
                if (account.status() != LedgerAccountStatus.ACTIVE) {
                    throw new LedgerException("Account is already archived.");
                }
                accountRepository.archive(connection, user.id(), accountId);
                userRepository.decrementLedgerAccountCount(connection, user.id());
                rateLimiter.record(connection, user.id());
                return accountRepository.findById(connection, user.id(), accountId)
                        .orElseThrow(() -> new LedgerException("Account not found after archive"));
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to archive account", exception);
        }
    }

    public List<LedgerTransaction> listTransactions(
            AcruetUser user,
            LocalDate fromDate,
            LocalDate toDate,
            UUID accountId) {
        try {
            return Database.inTransactionReturning(connection ->
                    transactionRepository.listByUser(connection, user.id(), fromDate, toDate, accountId));
        } catch (SQLException exception) {
            throw new LedgerException("Failed to list transactions", exception);
        }
    }

    public LedgerTransaction createTransaction(
            AcruetUser user,
            TransactionType transactionType,
            LocalDate transactionDate,
            String encryptedPayloadBase64,
            List<UUID> accountIds) {
        validateEncryptedBlob(encryptedPayloadBase64);
        if (accountIds == null || accountIds.isEmpty()) {
            throw new LedgerException("At least one account is required.");
        }
        if (transactionDate == null) {
            throw new LedgerException("Transaction date is required.");
        }
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                if (!accountRepository.allBelongToUser(connection, user.id(), accountIds)) {
                    throw new LedgerException("One or more accounts are invalid.");
                }
                byte[] encryptedPayload = EncryptedBlob.decode(encryptedPayloadBase64);
                transactionRepository.insert(
                        connection,
                        transactionId,
                        user.id(),
                        transactionType,
                        transactionDate,
                        encryptedPayload,
                        accountIds);
                userRepository.incrementTransactionCount(connection, user.id(), now);
                rateLimiter.record(connection, user.id());
                return transactionRepository.findById(connection, user.id(), transactionId)
                        .orElseThrow(() -> new LedgerException("Transaction not found after insert"));
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to create transaction", exception);
        }
    }

    public Optional<LedgerAccount> findAccount(AcruetUser user, UUID accountId) {
        try {
            return Database.inTransactionReturning(connection ->
                    accountRepository.findById(connection, user.id(), accountId));
        } catch (SQLException exception) {
            throw new LedgerException("Failed to load account", exception);
        }
    }

    private static void validateEncryptedBlob(String base64) {
        Optional<String> error = EncryptedBlob.validationError(base64);
        if (error.isPresent()) {
            throw new LedgerException(error.get());
        }
    }

    private void checkWriteRateLimit(Connection connection, UUID userId) throws SQLException {
        LedgerWriteRateLimiter.RateLimitResult result = rateLimiter.check(connection, userId, Instant.now());
        if (!result.permitted()) {
            throw new LedgerException(result.message());
        }
    }

    private AcruetUser reloadUser(Connection connection, UUID userId) throws SQLException {
        return userRepository.findById(connection, userId)
                .orElseThrow(() -> new LedgerException("User not found"));
    }
}
