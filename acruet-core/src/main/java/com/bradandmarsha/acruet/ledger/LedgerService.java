package com.bradandmarsha.acruet.ledger;

import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.household.HouseholdRepository;
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
 * Ledger account, bundle, and transaction orchestration.
 */
public final class LedgerService {

    static final int MAX_ACTIVE_BUNDLES = 10;

    private final UserRepository userRepository = new UserRepository();
    private final HouseholdRepository householdRepository = new HouseholdRepository();
    private final EnvelopeBundleRepository bundleRepository = new EnvelopeBundleRepository();
    private final LedgerAccountRepository accountRepository = new LedgerAccountRepository();
    private final LedgerTransactionRepository transactionRepository = new LedgerTransactionRepository();
    private final LedgerWriteRateLimiter rateLimiter = new LedgerWriteRateLimiter(transactionRepository);

    public List<EnvelopeBundle> listBundles(AcruetUser user, boolean includeArchived) {
        try {
            return Database.inTransactionReturning(connection ->
                    bundleRepository.listByHousehold(connection, user.householdId(), includeArchived));
        } catch (SQLException exception) {
            throw new LedgerException("Failed to list bundles", exception);
        }
    }

    public EnvelopeBundle createBundle(AcruetUser user, String encryptedNameBase64) {
        byte[] encryptedName = optionalEncryptedName(encryptedNameBase64);
        UUID bundleId = UUID.randomUUID();
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                if (bundleRepository.countActive(connection, user.householdId()) >= MAX_ACTIVE_BUNDLES) {
                    throw new LedgerException("Bundle limit reached (" + MAX_ACTIVE_BUNDLES + ").");
                }
                bundleRepository.insert(connection, bundleId, user.householdId(), encryptedName);
                householdRepository.incrementBundleCount(connection, user.householdId());
                rateLimiter.record(connection, user.id());
                return bundleRepository.findById(connection, user.householdId(), bundleId)
                        .orElseThrow(() -> new LedgerException("Bundle not found after insert"));
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to create bundle", exception);
        }
    }

    public EnvelopeBundle updateBundleName(AcruetUser user, UUID bundleId, String encryptedNameBase64) {
        validateEncryptedBlob(encryptedNameBase64);
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                byte[] encryptedName = EncryptedBlob.decode(encryptedNameBase64);
                bundleRepository.updateEncryptedName(connection, user.householdId(), bundleId, encryptedName);
                rateLimiter.record(connection, user.id());
                return bundleRepository.findById(connection, user.householdId(), bundleId)
                        .orElseThrow(() -> new LedgerException("Bundle not found"));
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to update bundle", exception);
        }
    }

    public EnvelopeBundle archiveBundle(AcruetUser user, UUID bundleId) {
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                EnvelopeBundle bundle = bundleRepository.findById(connection, user.householdId(), bundleId)
                        .orElseThrow(() -> new LedgerException("Bundle not found"));
                if (bundle.status() != EnvelopeBundleStatus.ACTIVE) {
                    throw new LedgerException("Bundle is already archived.");
                }
                if (bundleRepository.countActiveAccounts(connection, bundleId) > 0) {
                    throw new LedgerException("Archive all envelopes in the bundle first.");
                }
                if (bundleRepository.countActive(connection, user.householdId()) <= 1) {
                    throw new LedgerException("Cannot archive the last active bundle.");
                }
                bundleRepository.archive(connection, user.householdId(), bundleId);
                householdRepository.decrementBundleCount(connection, user.householdId());
                rateLimiter.record(connection, user.id());
                return bundleRepository.findById(connection, user.householdId(), bundleId)
                        .orElseThrow(() -> new LedgerException("Bundle not found after archive"));
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to archive bundle", exception);
        }
    }

    public List<LedgerAccount> listAccounts(AcruetUser user, boolean includeArchived) {
        return listAccounts(user, includeArchived, null);
    }

    public List<LedgerAccount> listAccounts(AcruetUser user, boolean includeArchived, UUID bundleId) {
        try {
            return Database.inTransactionReturning(connection -> {
                EnvelopeBundle bundle = resolveBundle(connection, user.householdId(), bundleId);
                return accountRepository.listByBundle(
                        connection, user.householdId(), bundle.id(), includeArchived);
            });
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to list accounts", exception);
        }
    }

    public EnvelopeBundle resolveBundleForRequest(AcruetUser user, UUID bundleId) {
        try {
            return Database.inTransactionReturning(connection ->
                    resolveBundle(connection, user.householdId(), bundleId));
        } catch (LedgerException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new LedgerException("Failed to load bundle", exception);
        }
    }

    public LedgerAccount createAccount(AcruetUser user, String encryptedNameBase64) {
        return createAccount(user, encryptedNameBase64, null);
    }

    public LedgerAccount createAccount(AcruetUser user, String encryptedNameBase64, UUID bundleId) {
        validateEncryptedBlob(encryptedNameBase64);
        UUID accountId = UUID.randomUUID();
        try {
            return Database.inTransactionReturning(connection -> {
                checkWriteRateLimit(connection, user.id());
                EnvelopeBundle bundle = requireActiveBundle(connection, user.householdId(), bundleId);
                if (bundle.ledgerAccountCount() >= bundle.ledgerAccountLimit()) {
                    throw new LedgerException("Account limit reached (" + bundle.ledgerAccountLimit() + ").");
                }
                byte[] encryptedName = EncryptedBlob.decode(encryptedNameBase64);
                accountRepository.insert(
                        connection, accountId, user.householdId(), bundle.id(), encryptedName);
                bundleRepository.incrementLedgerAccountCount(connection, bundle.id());
                userRepository.incrementLedgerAccountCount(connection, user.householdId());
                rateLimiter.record(connection, user.id());
                return accountRepository.findById(connection, user.householdId(), accountId)
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
                accountRepository.updateEncryptedName(connection, user.householdId(), accountId, encryptedName);
                rateLimiter.record(connection, user.id());
                return accountRepository.findById(connection, user.householdId(), accountId)
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
                LedgerAccount account = accountRepository.findById(connection, user.householdId(), accountId)
                        .orElseThrow(() -> new LedgerException("Account not found"));
                if (account.status() != LedgerAccountStatus.ACTIVE) {
                    throw new LedgerException("Account is already archived.");
                }
                accountRepository.archive(connection, user.householdId(), accountId);
                bundleRepository.decrementLedgerAccountCount(connection, account.bundleId());
                userRepository.decrementLedgerAccountCount(connection, user.householdId());
                rateLimiter.record(connection, user.id());
                return accountRepository.findById(connection, user.householdId(), accountId)
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
        return listTransactions(user, fromDate, toDate, accountId, null);
    }

    public List<LedgerTransaction> listTransactions(
            AcruetUser user,
            LocalDate fromDate,
            LocalDate toDate,
            UUID accountId,
            UUID bundleId) {
        try {
            return Database.inTransactionReturning(connection -> {
                EnvelopeBundle bundle = resolveBundle(connection, user.householdId(), bundleId);
                return transactionRepository.listByHousehold(
                        connection, user.householdId(), bundle.id(), fromDate, toDate, accountId);
            });
        } catch (LedgerException exception) {
            throw exception;
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
        return createTransaction(user, transactionType, transactionDate, encryptedPayloadBase64, accountIds, null);
    }

    public LedgerTransaction createTransaction(
            AcruetUser user,
            TransactionType transactionType,
            LocalDate transactionDate,
            String encryptedPayloadBase64,
            List<UUID> accountIds,
            UUID bundleId) {
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
                UUID resolvedBundleId = resolveBundleIdForAccounts(
                        connection, user.householdId(), accountIds, bundleId);
                byte[] encryptedPayload = EncryptedBlob.decode(encryptedPayloadBase64);
                transactionRepository.insert(
                        connection,
                        transactionId,
                        user.householdId(),
                        resolvedBundleId,
                        transactionType,
                        transactionDate,
                        encryptedPayload,
                        accountIds);
                bundleRepository.incrementTransactionCount(connection, resolvedBundleId);
                userRepository.incrementTransactionCount(connection, user.id(), user.householdId(), now);
                rateLimiter.record(connection, user.id());
                return transactionRepository.findById(connection, user.householdId(), transactionId)
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
                    accountRepository.findById(connection, user.householdId(), accountId));
        } catch (SQLException exception) {
            throw new LedgerException("Failed to load account", exception);
        }
    }

    private EnvelopeBundle resolveBundle(Connection connection, UUID householdId, UUID bundleId)
            throws SQLException {
        if (bundleId != null) {
            return bundleRepository.findById(connection, householdId, bundleId)
                    .orElseThrow(() -> new LedgerException("Bundle not found"));
        }
        return bundleRepository.findDefault(connection, householdId)
                .orElseThrow(() -> new LedgerException("Default bundle not found"));
    }

    private EnvelopeBundle requireActiveBundle(Connection connection, UUID householdId, UUID bundleId)
            throws SQLException {
        EnvelopeBundle bundle = resolveBundle(connection, householdId, bundleId);
        if (bundle.status() != EnvelopeBundleStatus.ACTIVE) {
            throw new LedgerException("Bundle is archived.");
        }
        return bundle;
    }

    private UUID resolveBundleIdForAccounts(
            Connection connection,
            UUID householdId,
            List<UUID> accountIds,
            UUID requestedBundleId) throws SQLException {
        if (requestedBundleId != null) {
            requireActiveBundle(connection, householdId, requestedBundleId);
            if (!accountRepository.allBelongToBundle(connection, householdId, requestedBundleId, accountIds)) {
                throw new LedgerException("One or more accounts are not in that bundle.");
            }
            return requestedBundleId;
        }
        if (!accountRepository.allBelongToHousehold(connection, householdId, accountIds)) {
            throw new LedgerException("One or more accounts are invalid.");
        }
        return accountRepository.singleBundleId(connection, householdId, accountIds)
                .orElseThrow(() -> new LedgerException("Accounts must belong to a single bundle."));
    }

    private static byte[] optionalEncryptedName(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        validateEncryptedBlob(base64);
        return EncryptedBlob.decode(base64);
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
}
