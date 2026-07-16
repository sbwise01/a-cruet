package com.bradandmarsha.acruet.keys;

import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.user.AcruetUser;
import com.bradandmarsha.acruet.user.UserEncryptionKey;
import com.bradandmarsha.acruet.user.UserEncryptionRepository;
import com.bradandmarsha.acruet.user.UserRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side key lifecycle: store wrapped DEK, confirm recovery, rotate wrap (Phase 7).
 */
public final class KeyService {

    private final UserRepository userRepository = new UserRepository();
    private final UserEncryptionRepository encryptionRepository = new UserEncryptionRepository();

    public Optional<AcruetUser> findUser(String keycloakUserId) {
        try {
            return Database.inTransactionReturning(connection ->
                    userRepository.findByKeycloakUserId(connection, keycloakUserId));
        } catch (SQLException exception) {
            throw new KeyServiceException("Failed to load user", exception);
        }
    }

    public void recordLogin(AcruetUser user, Instant loginAt) {
        try {
            Database.inTransaction(connection ->
                    userRepository.updateLastLogin(connection, user.id(), loginAt));
        } catch (SQLException exception) {
            throw new KeyServiceException("Failed to record login", exception);
        }
    }

    public KeyStatus status(AcruetUser user) {
        try {
            return Database.inTransactionReturning(connection -> {
                boolean hasWrappedDek = encryptionRepository.existsForUser(connection, user.id());
                return new KeyStatus(user.keySetupComplete(), hasWrappedDek);
            });
        } catch (SQLException exception) {
            throw new KeyServiceException("Failed to load key status", exception);
        }
    }

    public Optional<WrappedDekResponse> wrappedDek(AcruetUser user) {
        try {
            return Database.inTransactionReturning(connection ->
                    encryptionRepository.findByUserId(connection, user.id())
                            .map(WrappedDekResponse::from));
        } catch (SQLException exception) {
            throw new KeyServiceException("Failed to load wrapped DEK", exception);
        }
    }

    public void storeInitialWrappedDek(AcruetUser user, WrappedDekPayload payload) {
        Optional<String> validationError = payload.validationError();
        if (validationError.isPresent()) {
            throw new KeyServiceException(validationError.get());
        }
        if (user.keySetupComplete()) {
            throw new KeyServiceException("Encryption key is already configured.");
        }
        try {
            Database.inTransaction(connection -> {
                if (encryptionRepository.existsForUser(connection, user.id())) {
                    throw new KeyServiceException("Wrapped DEK already exists.");
                }
                encryptionRepository.insert(connection, toEncryptionKey(user.id(), payload));
            });
        } catch (KeyServiceException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new KeyServiceException("Failed to store wrapped DEK", exception);
        }
    }

    public void confirmRecovery(AcruetUser user) {
        if (user.keySetupComplete()) {
            return;
        }
        try {
            Database.inTransaction(connection -> {
                if (!encryptionRepository.existsForUser(connection, user.id())) {
                    throw new KeyServiceException("Store a wrapped DEK before confirming recovery.");
                }
                userRepository.markKeySetupComplete(connection, user.id());
            });
        } catch (KeyServiceException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new KeyServiceException("Failed to confirm recovery", exception);
        }
    }

    public void rotateWrappedDek(AcruetUser user, WrappedDekPayload payload) {
        Optional<String> validationError = payload.validationError();
        if (validationError.isPresent()) {
            throw new KeyServiceException(validationError.get());
        }
        if (!user.keySetupComplete()) {
            throw new KeyServiceException("Complete key setup before rotating.");
        }
        try {
            Database.inTransaction(connection -> {
                if (!encryptionRepository.existsForUser(connection, user.id())) {
                    throw new KeyServiceException("No wrapped DEK exists to rotate.");
                }
                encryptionRepository.updateWrappedDek(connection, toEncryptionKey(user.id(), payload));
            });
        } catch (KeyServiceException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new KeyServiceException("Failed to rotate wrapped DEK", exception);
        }
    }

    public Optional<AcruetUser> reloadUser(AcruetUser user) {
        return findUser(user.keycloakUserId());
    }

    private static UserEncryptionKey toEncryptionKey(UUID userId, WrappedDekPayload payload) {
        return new UserEncryptionKey(
                userId,
                payload.wrappedDekBytes(),
                payload.wrapAlgorithm(),
                payload.kdfAlgorithm(),
                payload.kdfHash(),
                payload.kdfSaltBytes(),
                payload.kdfIterations(),
                Instant.EPOCH,
                Instant.EPOCH);
    }

    public record KeyStatus(boolean keySetupComplete, boolean hasWrappedDek) {
    }

    public record WrappedDekResponse(
            String kdfAlgorithm,
            String kdfHash,
            String kdfSalt,
            int kdfIterations,
            String wrapAlgorithm,
            String wrappedDek) {

        static WrappedDekResponse from(UserEncryptionKey key) {
            return new WrappedDekResponse(
                    key.kdfAlgorithm(),
                    key.kdfHash(),
                    Base64.getEncoder().encodeToString(key.kdfSalt()),
                    key.kdfIterations(),
                    key.wrapAlgorithm(),
                    Base64.getEncoder().encodeToString(key.wrappedDek()));
        }
    }
}
