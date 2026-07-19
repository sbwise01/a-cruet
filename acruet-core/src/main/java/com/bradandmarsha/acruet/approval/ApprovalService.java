package com.bradandmarsha.acruet.approval;

import com.bradandmarsha.acruet.config.KeycloakAdminSettings;
import com.bradandmarsha.acruet.config.SmtpSettings;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.keycloak.KeycloakAdminClient;
import com.bradandmarsha.acruet.keycloak.KeycloakAdminException;
import com.bradandmarsha.acruet.keycloak.ProvisionedUser;
import com.bradandmarsha.acruet.mail.MailSender;
import com.bradandmarsha.acruet.signup.ApplicationStatus;
import com.bradandmarsha.acruet.signup.SignupPolicy;
import com.bradandmarsha.acruet.signup.SignupRepository;
import com.bradandmarsha.acruet.user.UserRepository;

import jakarta.mail.MessagingException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Admin approval workflow: Keycloak provisioning, a-cruet user scaffold, audit, email (Phase 6).
 */
public final class ApprovalService {

    private static final Logger LOGGER = Logger.getLogger(ApprovalService.class.getName());
    private static final String TARGET_TYPE_SIGNUP = "signup_application";
    private static final String ENV_USER_BASE_URL = "ACRUET_USER_BASE_URL";
    private static final String DEFAULT_USER_BASE_URL = "https://acruet.home.bradandmarsha.com";

    private final SignupRepository signupRepository;
    private final UserRepository userRepository;
    private final AdminAuditRepository auditRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final MailSender mailSender;
    private final String userBaseUrl;

    public ApprovalService(
            SignupRepository signupRepository,
            UserRepository userRepository,
            AdminAuditRepository auditRepository,
            KeycloakAdminClient keycloakAdminClient,
            MailSender mailSender,
            String userBaseUrl) {
        this.signupRepository = signupRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.mailSender = mailSender;
        this.userBaseUrl = trimTrailingSlash(userBaseUrl);
    }

    public static ApprovalService fromEnvironment() {
        return new ApprovalService(
                new SignupRepository(),
                new UserRepository(),
                new AdminAuditRepository(),
                new KeycloakAdminClient(KeycloakAdminSettings.fromEnvironment()),
                new MailSender(SmtpSettings.fromEnvironment()),
                envOrDefault(ENV_USER_BASE_URL, DEFAULT_USER_BASE_URL));
    }

    public List<SignupRepository.PendingApplication> listPending() {
        try {
            return signupRepository.listPendingApproval();
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to list pending applications", exception);
            return List.of();
        }
    }

    public ActionResult approve(UUID applicationId, AdminActor admin) {
        try {
            Optional<com.bradandmarsha.acruet.signup.SignupApplication> applicationOptional =
                    signupRepository.findById(applicationId);
            if (applicationOptional.isEmpty()) {
                return ActionResult.error("Application not found.");
            }
            com.bradandmarsha.acruet.signup.SignupApplication application = applicationOptional.get();
            if (application.status() != ApplicationStatus.PENDING_APPROVAL) {
                return ActionResult.error("Application is not awaiting approval.");
            }

            ProvisionedUser provisioned =
                    keycloakAdminClient.provisionUser(application.email(), application.fullName());
            UUID acruetUserId = UUID.randomUUID();

            Database.inTransaction(connection -> {
                if (!signupRepository.markApproved(connection, applicationId)) {
                    throw new IllegalStateException("Application is no longer pending approval.");
                }
                userRepository.insert(
                        connection,
                        acruetUserId,
                        provisioned.keycloakUserId(),
                        application.email(),
                        application.fullName(),
                        application.phone(),
                        application.mailingAddress(),
                        applicationId);
                auditRepository.insert(
                        connection,
                        admin.keycloakUserId(),
                        admin.email(),
                        ApprovalAction.APPROVE_SIGNUP,
                        TARGET_TYPE_SIGNUP,
                        applicationId,
                        "Provisioned Keycloak user " + provisioned.keycloakUserId());
            });

            try {
                mailSender.send(
                        application.email(),
                        "Your a-cruet application was approved",
                        approvalEmailBody(application.fullName(), provisioned.temporaryPassword()));
            } catch (MessagingException mailException) {
                LOGGER.log(
                        Level.WARNING,
                        "Approval email failed for {0}",
                        application.email());
                LOGGER.log(Level.FINE, "Approval mail failure detail", mailException);
                return ActionResult.success(
                        "Application approved and Keycloak user created, but the approval email could not be sent.");
            }

            return ActionResult.success(
                    "Approved "
                            + application.email()
                            + ". Keycloak user created and approval email sent.");
        } catch (KeycloakAdminException exception) {
            LOGGER.log(Level.WARNING, "Keycloak provisioning failed", exception);
            return ActionResult.error(exception.getMessage());
        } catch (IllegalStateException exception) {
            return ActionResult.error(exception.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Approval failed", exception);
            return ActionResult.error("Approval failed. Please try again.");
        }
    }

    public ActionResult reject(UUID applicationId, AdminActor admin) {
        try {
            Optional<com.bradandmarsha.acruet.signup.SignupApplication> applicationOptional =
                    signupRepository.findById(applicationId);
            if (applicationOptional.isEmpty()) {
                return ActionResult.error("Application not found.");
            }
            com.bradandmarsha.acruet.signup.SignupApplication application = applicationOptional.get();
            if (application.status() != ApplicationStatus.PENDING_APPROVAL) {
                return ActionResult.error("Application is not awaiting approval.");
            }

            Instant now = Instant.now();
            SignupRepository.RejectionResult rejectionResult = Database.inTransactionReturning(
                    connection -> {
                        SignupRepository.RejectionResult result =
                                signupRepository.markRejected(connection, applicationId, now);
                        if (result == null) {
                            throw new IllegalStateException("Application is no longer pending approval.");
                        }
                        auditRepository.insert(
                                connection,
                                admin.keycloakUserId(),
                                admin.email(),
                                ApprovalAction.REJECT_SIGNUP,
                                TARGET_TYPE_SIGNUP,
                                applicationId,
                                "Rejection count " + result.rejectionCount());
                        return result;
                    });

            try {
                mailSender.send(
                        application.email(),
                        "Your a-cruet application was not approved",
                        rejectionEmailBody(application.fullName(), rejectionResult));
            } catch (MessagingException mailException) {
                LOGGER.log(
                        Level.WARNING,
                        "Rejection email failed for {0}",
                        application.email());
                LOGGER.log(Level.FINE, "Rejection mail failure detail", mailException);
                return ActionResult.success(
                        "Application rejected, but the rejection email could not be sent.");
            }

            return ActionResult.success("Rejected " + application.email() + " and sent rejection email.");
        } catch (IllegalStateException exception) {
            return ActionResult.error(exception.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Rejection failed", exception);
            return ActionResult.error("Rejection failed. Please try again.");
        }
    }

    private String approvalEmailBody(String fullName, String temporaryPassword) {
        return """
                Hello %s,

                Your a-cruet application has been approved.

                Sign in at: %s/auth/login

                Your temporary password is: %s
                You will be asked to set a new password on first sign-in.

                Welcome to a-cruet!
                """
                .formatted(fullName, userBaseUrl, temporaryPassword);
    }

    private String rejectionEmailBody(String fullName, SignupRepository.RejectionResult rejectionResult) {
        String followUp = rejectionResult.status() == ApplicationStatus.BLOCKED
                ? "This email is now blocked from re-applying. Contact an administrator if you believe this is an error."
                : "You may submit a new application after "
                        + SignupPolicy.COOLDOWN_DAYS
                        + " days.";
        return """
                Hello %s,

                Thank you for your interest in a-cruet. Your application was not approved at this time.

                %s
                """
                .formatted(fullName, followUp);
    }

    private static String envOrDefault(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    public record AdminActor(String keycloakUserId, String email) {
    }

    public record ActionResult(boolean success, String message) {
        static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        static ActionResult error(String message) {
            return new ActionResult(false, message);
        }
    }
}
