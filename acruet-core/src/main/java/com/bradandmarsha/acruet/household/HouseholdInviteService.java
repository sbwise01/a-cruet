package com.bradandmarsha.acruet.household;

import com.bradandmarsha.acruet.auth.OidcSettings;
import com.bradandmarsha.acruet.config.SmtpSettings;
import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.keys.WrappedDekPayload;
import com.bradandmarsha.acruet.mail.MailSender;
import com.bradandmarsha.acruet.signup.SignupPolicy;
import com.bradandmarsha.acruet.signup.SignupRepository;
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
    private static final int VERIFICATION_VALID_HOURS = 24;
    private static final String INVITE_PLACEHOLDER_NAME = "Invited member";
    private static final String INVITE_PLACEHOLDER_REASON = "Household member invitation";
    private static final String INVITE_PLACEHOLDER_PHONE = "Pending";
    private static final String INVITE_PLACEHOLDER_ADDRESS = "Pending profile completion";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final HouseholdRepository householdRepository;
    private final HouseholdInviteRepository inviteRepository;
    private final SignupRepository signupRepository;
    private final UserRepository userRepository;
    private final MailSender mailSender;
    private final String baseUrl;

    public HouseholdInviteService(
            HouseholdRepository householdRepository,
            HouseholdInviteRepository inviteRepository,
            SignupRepository signupRepository,
            UserRepository userRepository,
            MailSender mailSender,
            String baseUrl) {
        this.householdRepository = householdRepository;
        this.inviteRepository = inviteRepository;
        this.signupRepository = signupRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static HouseholdInviteService fromEnvironment() {
        return new HouseholdInviteService(
                new HouseholdRepository(),
                new HouseholdInviteRepository(),
                new SignupRepository(),
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
            PendingInviteMail pending = Database.inTransactionReturning(connection -> {
                if (userRepository.findByEmail(connection, normalizedEmail).isPresent()) {
                    throw new InviteFailure("That email already belongs to an a-cruet user.");
                }
                SignupPolicy.Decision signupDecision = SignupPolicy.evaluate(
                        signupRepository.findLatestByEmail(connection, normalizedEmail), now);
                if (signupDecision != SignupPolicy.Decision.ALLOW) {
                    throw new InviteFailure(signupPolicyMessage(signupDecision));
                }
                int members = householdRepository.countMembers(connection, inviter.householdId());
                int pendingInvites = householdRepository.countPendingInvites(connection, inviter.householdId());
                if (members + pendingInvites >= MAX_HOUSEHOLD_MEMBERS) {
                    throw new InviteFailure(
                            "This household already has the maximum of "
                                    + MAX_HOUSEHOLD_MEMBERS
                                    + " members and pending invites.");
                }
                if (inviteRepository.hasPendingInviteForEmail(
                        connection, inviter.householdId(), normalizedEmail)) {
                    throw new InviteFailure("A pending invite already exists for that email.");
                }

                UUID inviteId = UUID.randomUUID();
                UUID applicationId = UUID.randomUUID();
                String verifyToken = SignupTokens.newToken();
                Instant inviteExpiresAt = now.plus(INVITE_VALID_DAYS, ChronoUnit.DAYS);
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
                        inviteExpiresAt);
                signupRepository.insertApplication(
                        connection,
                        applicationId,
                        normalizedEmail,
                        INVITE_PLACEHOLDER_NAME,
                        INVITE_PLACEHOLDER_REASON,
                        INVITE_PLACEHOLDER_PHONE,
                        INVITE_PLACEHOLDER_ADDRESS,
                        SignupTokens.hash(verifyToken),
                        now.plus(VERIFICATION_VALID_HOURS, ChronoUnit.HOURS),
                        null,
                        inviteId);
                return new PendingInviteMail(
                        inviteId,
                        applicationId,
                        normalizedEmail,
                        verifyToken,
                        request.inviteToken(),
                        inviteExpiresAt,
                        inviter.displayName());
            });

            try {
                mailSender.send(
                        pending.email(),
                        "You are invited to join an a-cruet household",
                        inviteNotificationEmailBody(
                                pending.inviterDisplayName(),
                                pending.inviteToken(),
                                INVITE_VALID_DAYS));
                mailSender.send(
                        pending.email(),
                        "Verify your a-cruet application",
                        inviteVerificationEmailBody(
                                pending.inviterDisplayName(),
                                baseUrl + "/signup/verify?token=" + pending.verifyToken()));
            } catch (MessagingException mailException) {
                rollbackPendingInvite(pending);
                LOGGER.log(Level.WARNING, "Household invite email failed for {0}", normalizedEmail);
                LOGGER.log(Level.FINE, "Household invite mail failure detail", mailException);
                return CreateResult.error("Unable to send the invitation email right now. Please try again later.");
            }

            return CreateResult.sent(pending.email(), pending.inviteExpiresAt());
        } catch (InviteFailure failure) {
            return CreateResult.error(failure.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Household invite failed", exception);
            return CreateResult.error("Unable to send the invitation right now. Please try again later.");
        }
    }

    private void rollbackPendingInvite(PendingInviteMail pending) {
        try {
            Database.inTransaction(connection -> {
                signupRepository.deleteApplication(connection, pending.applicationId());
                inviteRepository.deleteById(connection, pending.inviteId());
            });
        } catch (SQLException rollbackException) {
            LOGGER.log(Level.WARNING, "Failed to roll back household invite after mail failure", rollbackException);
        }
    }

    private static String inviteNotificationEmailBody(
            String inviterDisplayName, String inviteToken, int inviteValidDays) {
        return """
                Hello,

                %s invited you to join their a-cruet household ledger.

                You will receive a separate email shortly with a link to verify your email address. After an administrator approves your request, sign in and complete your profile information on first login.

                Keep this invitation token safe — you will need it when setting up household encryption after approval:

                %s

                This invitation is valid for %d days.
                """
                .formatted(inviterDisplayName, inviteToken, inviteValidDays);
    }

    private static String inviteVerificationEmailBody(String inviterDisplayName, String verifyUrl) {
        return """
                Hello,

                %s invited you to join an a-cruet household on a-cruet. Verify your email to queue your application for admin review:

                %s

                This link expires in 24 hours. If you did not expect this invitation, you can ignore this message.
                """
                .formatted(inviterDisplayName, verifyUrl);
    }

    private static String signupPolicyMessage(SignupPolicy.Decision decision) {
        return switch (decision) {
            case IN_PROGRESS -> "That email already has an application in progress.";
            case COOLDOWN -> "That email must wait "
                    + SignupPolicy.COOLDOWN_DAYS
                    + " days after a rejection before re-applying.";
            case BLOCKED -> "That email is blocked from re-applying. Contact an administrator.";
            case ALLOW -> "";
        };
    }

    private record PendingInviteMail(
            UUID inviteId,
            UUID applicationId,
            String email,
            String verifyToken,
            String inviteToken,
            Instant inviteExpiresAt,
            String inviterDisplayName) {
    }

    private static final class InviteFailure extends RuntimeException {
        private InviteFailure(String message) {
            super(message);
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
