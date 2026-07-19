package com.bradandmarsha.acruet.household;

import com.bradandmarsha.acruet.auth.OidcSettings;
import com.bradandmarsha.acruet.config.SmtpSettings;
import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.keys.WrappedDekPayload;
import com.bradandmarsha.acruet.mail.MailSender;
import com.bradandmarsha.acruet.signup.SignupTokens;
import com.bradandmarsha.acruet.user.AcruetUser;
import com.bradandmarsha.acruet.user.UserRepository;

import jakarta.mail.MessagingException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Household member invite workflow (Phase 12c).
 */
public final class HouseholdInviteService {

    private static final Logger LOGGER = Logger.getLogger(HouseholdInviteService.class.getName());

    public static final int MAX_HOUSEHOLD_MEMBERS = 5;
    private static final int INVITE_VALID_DAYS = 7;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final HouseholdRepository householdRepository;
    private final HouseholdInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final MailSender mailSender;
    private final String baseUrl;

    public HouseholdInviteService(
            HouseholdRepository householdRepository,
            HouseholdInviteRepository inviteRepository,
            UserRepository userRepository,
            MailSender mailSender,
            String baseUrl) {
        this.householdRepository = householdRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static HouseholdInviteService fromEnvironment() {
        return new HouseholdInviteService(
                new HouseholdRepository(),
                new HouseholdInviteRepository(),
                new UserRepository(),
                new MailSender(SmtpSettings.fromEnvironment()),
                OidcSettings.fromEnvironment().baseUrl());
    }

    public CreateResult createInvite(AcruetUser inviter, InviteRequest request, Instant now) {
        if (!inviter.keySetupComplete()) {
            return CreateResult.error("Complete encryption key setup before inviting members.");
        }
        Optional<String> validationError = validateInviteRequest(request);
        if (validationError.isPresent()) {
            return CreateResult.error(validationError.get());
        }

        String normalizedEmail = request.email().trim();
        byte[] encryptedPayload = EncryptedBlob.decode(request.wrappedDek());

        try {
            return Database.inTransactionReturning(connection -> {
                if (userRepository.findByEmail(connection, normalizedEmail).isPresent()) {
                    return CreateResult.error("That email already belongs to an a-cruet user.");
                }
                int members = householdRepository.countMembers(connection, inviter.householdId());
                int pendingInvites = householdRepository.countPendingInvites(connection, inviter.householdId());
                if (members + pendingInvites >= MAX_HOUSEHOLD_MEMBERS) {
                    return CreateResult.error(
                            "This household already has the maximum of "
                                    + MAX_HOUSEHOLD_MEMBERS
                                    + " members and pending invites.");
                }
                if (inviteRepository.hasPendingInviteForEmail(
                        connection, inviter.householdId(), normalizedEmail)) {
                    return CreateResult.error("A pending invite already exists for that email.");
                }

                UUID inviteId = UUID.randomUUID();
                Instant expiresAt = now.plus(INVITE_VALID_DAYS, ChronoUnit.DAYS);
                inviteRepository.insert(
                        connection,
                        inviteId,
                        inviter.householdId(),
                        normalizedEmail,
                        SignupTokens.hash(request.inviteToken()),
                        inviter.id(),
                        encryptedPayload,
                        request.wrapAlgorithm(),
                        request.kdfAlgorithm(),
                        request.kdfHash(),
                        EncryptedBlob.decode(request.kdfSalt()),
                        request.kdfIterations(),
                        expiresAt);

                String signupUrl = baseUrl + "/signup?invite=" + request.inviteToken();
                try {
                    mailSender.send(
                            normalizedEmail,
                            "You are invited to join an a-cruet household",
                            """
                                    Hello,

                                    %s invited you to join their a-cruet household ledger.

                                    Apply using this link (valid for %d days):

                                    %s

                                    Your email must match this invitation. After applying, an administrator will review your request.
                                    """
                                    .formatted(inviter.displayName(), INVITE_VALID_DAYS, signupUrl));
                } catch (MessagingException mailException) {
                    throw new IllegalStateException("Invite email failed", mailException);
                }

                return CreateResult.sent(normalizedEmail, expiresAt);
            });
        } catch (IllegalStateException mailFailure) {
            LOGGER.log(Level.WARNING, "Household invite email failed for {0}", normalizedEmail);
            return CreateResult.error("Unable to send the invitation email right now. Please try again later.");
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Household invite failed", exception);
            return CreateResult.error("Unable to send the invitation right now. Please try again later.");
        }
    }

    public Optional<SignupInvitePreview> previewSignupInvite(String inviteToken, Instant now) {
        if (inviteToken == null || inviteToken.isBlank()) {
            return Optional.empty();
        }
        try {
            return Database.inTransactionReturning(connection -> {
                Optional<HouseholdInvite> inviteOptional =
                        inviteRepository.findByTokenHash(connection, SignupTokens.hash(inviteToken.trim()), now);
                if (inviteOptional.isEmpty()) {
                    return Optional.empty();
                }
                HouseholdInvite invite = inviteOptional.get();
                if (invite.status() != HouseholdInviteStatus.PENDING) {
                    return Optional.empty();
                }
                return Optional.of(new SignupInvitePreview(invite.id(), invite.email(), invite.expiresAt()));
            });
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Failed to preview household invite", exception);
            return Optional.empty();
        }
    }

    public Optional<HouseholdInvite> resolveSignupInvite(Connection connection, String inviteToken, String email, Instant now)
            throws SQLException {
        if (inviteToken == null || inviteToken.isBlank()) {
            return Optional.empty();
        }
        Optional<HouseholdInvite> inviteOptional =
                inviteRepository.findByTokenHash(connection, SignupTokens.hash(inviteToken.trim()), now);
        if (inviteOptional.isEmpty()) {
            return Optional.empty();
        }
        HouseholdInvite invite = inviteOptional.get();
        if (invite.status() != HouseholdInviteStatus.PENDING) {
            return Optional.empty();
        }
        if (!invite.email().equalsIgnoreCase(email.trim())) {
            return Optional.empty();
        }
        return Optional.of(invite);
    }

    private static Optional<String> validateInviteRequest(InviteRequest request) {
        if (request == null) {
            return Optional.of("Invite request is required.");
        }
        if (isBlank(request.email()) || !EMAIL_PATTERN.matcher(request.email().trim()).matches()) {
            return Optional.of("A valid email address is required.");
        }
        if (isBlank(request.inviteToken())) {
            return Optional.of("Invite token is required.");
        }
        if (validationErrorForBase64Blob(request.wrappedDek(), WrappedDekPayload.MIN_WRAPPED_DEK_BYTES).isPresent()) {
            return Optional.of("Invalid invite encryption payload.");
        }
        if (validationErrorForBase64Blob(request.kdfSalt(), WrappedDekPayload.MIN_SALT_BYTES).isPresent()) {
            return Optional.of("Invalid invite encryption metadata.");
        }
        if (isBlank(request.wrapAlgorithm()) || isBlank(request.kdfAlgorithm()) || isBlank(request.kdfHash())) {
            return Optional.of("Invite encryption metadata is incomplete.");
        }
        if (!WrappedDekPayload.WRAP_ALGORITHM.equals(request.wrapAlgorithm())) {
            return Optional.of("Unsupported invite wrap algorithm.");
        }
        if (request.kdfIterations() < 0) {
            return Optional.of("Invalid invite encryption metadata.");
        }
        return Optional.empty();
    }

    private static Optional<String> validationErrorForBase64Blob(String base64, int minBytes) {
        if (base64 == null || base64.isBlank()) {
            return Optional.of("Value is required.");
        }
        byte[] bytes = EncryptedBlob.decode(base64);
        if (bytes == null) {
            return Optional.of("Value is not valid base64.");
        }
        if (bytes.length < minBytes) {
            return Optional.of("Value is too short.");
        }
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record InviteRequest(
            String email,
            String inviteToken,
            String wrappedDek,
            String wrapAlgorithm,
            String kdfAlgorithm,
            String kdfHash,
            String kdfSalt,
            int kdfIterations) {
    }

    public record SignupInvitePreview(UUID inviteId, String email, Instant expiresAt) {
    }

    public record CreateResult(boolean success, String message, Optional<String> email, Optional<Instant> expiresAt) {
        static CreateResult sent(String email, Instant expiresAt) {
            return new CreateResult(
                    true,
                    "Invitation sent to " + email + ".",
                    Optional.of(email),
                    Optional.of(expiresAt));
        }

        static CreateResult error(String message) {
            return new CreateResult(false, message, Optional.empty(), Optional.empty());
        }
    }
}
