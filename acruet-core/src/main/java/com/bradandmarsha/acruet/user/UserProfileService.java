package com.bradandmarsha.acruet.user;

import com.bradandmarsha.acruet.db.Database;

import java.sql.SQLException;
import java.util.Optional;

/**
 * User profile contact fields and ledger preferences.
 */
public final class UserProfileService {

    private final UserRepository userRepository = new UserRepository();

    public Optional<AcruetUser> findUser(String keycloakUserId) {
        try {
            return Database.inTransactionReturning(connection ->
                    userRepository.findByKeycloakUserId(connection, keycloakUserId));
        } catch (SQLException exception) {
            throw new UserProfileException("Failed to load user", exception);
        }
    }

    public ProfileView profile(AcruetUser user) {
        return new ProfileView(
                user.displayName(),
                user.email(),
                user.phone(),
                user.mailingAddress(),
                user.allowNegativeWithdraw());
    }

    public AcruetUser updateProfile(AcruetUser user, ProfileUpdate update) {
        Optional<String> validationError = validate(update);
        if (validationError.isPresent()) {
            throw new UserProfileException(validationError.get());
        }
        try {
            return Database.inTransactionReturning(connection -> {
                userRepository.updateProfile(
                        connection,
                        user.id(),
                        update.displayName().trim(),
                        update.phone().trim(),
                        update.mailingAddress().trim(),
                        update.allowNegativeWithdraw());
                return userRepository.findById(connection, user.id())
                        .orElseThrow(() -> new UserProfileException("User not found after update"));
            });
        } catch (UserProfileException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new UserProfileException("Failed to update profile", exception);
        }
    }

    static Optional<String> validate(ProfileUpdate update) {
        if (isBlank(update.displayName())) {
            return Optional.of("Full name is required.");
        }
        if (isBlank(update.phone())) {
            return Optional.of("Phone number is required.");
        }
        if (isBlank(update.mailingAddress())) {
            return Optional.of("Mailing address is required.");
        }
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ProfileView(
            String displayName,
            String email,
            String phone,
            String mailingAddress,
            boolean allowNegativeWithdraw) {
    }

    public record ProfileUpdate(
            String displayName, String phone, String mailingAddress, boolean allowNegativeWithdraw) {
    }

    public static final class UserProfileException extends RuntimeException {
        public UserProfileException(String message) {
            super(message);
        }

        public UserProfileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
