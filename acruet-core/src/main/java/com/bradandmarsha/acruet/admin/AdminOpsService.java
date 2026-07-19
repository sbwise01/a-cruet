package com.bradandmarsha.acruet.admin;

import com.bradandmarsha.acruet.approval.AdminAuditRepository;
import com.bradandmarsha.acruet.approval.ApprovalAction;
import com.bradandmarsha.acruet.approval.ApprovalService;
import com.bradandmarsha.acruet.auth.OidcSettings;
import com.bradandmarsha.acruet.config.AdminAlertSettings;
import com.bradandmarsha.acruet.config.KeycloakAdminSettings;
import com.bradandmarsha.acruet.config.SmtpSettings;
import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.keycloak.KeycloakAdminClient;
import com.bradandmarsha.acruet.keycloak.KeycloakAdminException;
import com.bradandmarsha.acruet.mail.MailSender;
import com.bradandmarsha.acruet.signup.SignupApplication;
import com.bradandmarsha.acruet.signup.SignupRepository;
import com.bradandmarsha.acruet.user.AcruetUser;
import com.bradandmarsha.acruet.user.LoginAnomalyRepository;
import com.bradandmarsha.acruet.user.UserRepository;

import jakarta.mail.MessagingException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administrator operations: user list, suspend, offboard, role grants, signup unblock (Phase 11).
 */
public final class AdminOpsService {

    private static final Logger LOGGER = Logger.getLogger(AdminOpsService.class.getName());
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private static final String TARGET_TYPE_USER = "acruet_user";
    private static final String TARGET_TYPE_SIGNUP = "signup_application";
    private static final String TARGET_TYPE_ANOMALY = "login_anomaly";
    private static final int OFFBOARD_EXPORT_DAYS = 7;
    private static final String ENV_USER_BASE_URL = "ACRUET_USER_BASE_URL";
    private static final String DEFAULT_USER_BASE_URL = "https://acruet.home.bradandmarsha.com";
    private static final String ENV_ADMIN_BASE_URL = "ACRUET_BASE_URL";
    private static final String DEFAULT_ADMIN_BASE_URL = "https://acruet-admin.home.bradandmarsha.com";

    public static final ApprovalService.AdminActor CRON_ACTOR =
            new ApprovalService.AdminActor("system:cron", null);

    private final UserRepository userRepository;
    private final UserOffboardRepository offboardRepository;
    private final SignupRepository signupRepository;
    private final LoginAnomalyRepository anomalyRepository;
    private final AdminAuditRepository auditRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final MailSender mailSender;
    private final String adminRole;
    private final String userBaseUrl;
    private final String adminBaseUrl;
    private final List<String> alertEmails;

    public AdminOpsService(
            UserRepository userRepository,
            UserOffboardRepository offboardRepository,
            SignupRepository signupRepository,
            LoginAnomalyRepository anomalyRepository,
            AdminAuditRepository auditRepository,
            KeycloakAdminClient keycloakAdminClient,
            MailSender mailSender,
            String adminRole,
            String userBaseUrl,
            String adminBaseUrl,
            List<String> alertEmails) {
        this.userRepository = userRepository;
        this.offboardRepository = offboardRepository;
        this.signupRepository = signupRepository;
        this.anomalyRepository = anomalyRepository;
        this.auditRepository = auditRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.mailSender = mailSender;
        this.adminRole = adminRole;
        this.userBaseUrl = trimTrailingSlash(userBaseUrl);
        this.adminBaseUrl = trimTrailingSlash(adminBaseUrl);
        this.alertEmails = List.copyOf(alertEmails);
    }

    public static AdminOpsService fromEnvironment() {
        return new AdminOpsService(
                new UserRepository(),
                new UserOffboardRepository(),
                new SignupRepository(),
                new LoginAnomalyRepository(),
                new AdminAuditRepository(),
                new KeycloakAdminClient(KeycloakAdminSettings.fromEnvironment()),
                new MailSender(SmtpSettings.fromEnvironment()),
                OidcSettings.DEFAULT_ADMIN_ROLE,
                envOrDefault(ENV_USER_BASE_URL, DEFAULT_USER_BASE_URL),
                envOrDefault(ENV_ADMIN_BASE_URL, DEFAULT_ADMIN_BASE_URL),
                AdminAlertSettings.alertEmailsFromEnvironment());
    }

    public List<OperationalUserView> listOperationalUsers() {
        try {
            List<UserRepository.OperationalUserRow> rows = userRepository.listOperational();
            List<OperationalUserView> views = new ArrayList<>(rows.size());
            for (UserRepository.OperationalUserRow row : rows) {
                boolean isAdmin = keycloakAdminClient.hasRealmRole(row.user().keycloakUserId(), adminRole);
                views.add(new OperationalUserView(row, isAdmin));
            }
            return views;
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to list operational users", exception);
            return List.of();
        }
    }

    public List<SignupApplication> listBlockedSignups() {
        try {
            return signupRepository.listBlocked();
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to list blocked signups", exception);
            return List.of();
        }
    }

    public List<LoginAnomalyRepository.LoginAnomaly> listRecentAnomalies(int limit) {
        try {
            return anomalyRepository.listRecent(limit);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to list login anomalies", exception);
            return List.of();
        }
    }

    public ApprovalService.ActionResult grantAdmin(UUID userId, ApprovalService.AdminActor admin) {
        return changeAdminRole(userId, admin, true);
    }

    public ApprovalService.ActionResult revokeAdmin(UUID userId, ApprovalService.AdminActor admin) {
        return changeAdminRole(userId, admin, false);
    }

    public ApprovalService.ActionResult suspend(UUID userId, int days, ApprovalService.AdminActor admin) {
        if (days < 1 || days > 365) {
            return ApprovalService.ActionResult.error("Suspension must be between 1 and 365 days.");
        }
        try {
            Optional<AcruetUser> userOptional = Database.inTransactionReturning(
                    connection -> userRepository.findById(connection, userId));
            if (userOptional.isEmpty()) {
                return ApprovalService.ActionResult.error("User not found.");
            }
            AcruetUser user = userOptional.get();
            if (isSelf(admin, user)) {
                return ApprovalService.ActionResult.error("You cannot suspend your own account.");
            }
            Instant now = Instant.now();
            Instant until = now.plus(days, ChronoUnit.DAYS);
            keycloakAdminClient.setUserEnabled(user.keycloakUserId(), false);
            Database.inTransaction(connection -> {
                userRepository.setSuspension(connection, userId, until, now);
                auditRepository.insert(
                        connection,
                        admin.keycloakUserId(),
                        admin.email(),
                        ApprovalAction.SUSPEND_USER,
                        TARGET_TYPE_USER,
                        userId,
                        days + " day suspension until " + DISPLAY_TIME.format(until));
            });
            sendSuspendEmail(user, days, until);
            return ApprovalService.ActionResult.success(
                    "Suspended " + user.email() + " for " + days + " day(s).");
        } catch (KeycloakAdminException exception) {
            LOGGER.log(Level.WARNING, "Suspend failed in Keycloak", exception);
            return ApprovalService.ActionResult.error(exception.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Suspend failed", exception);
            return ApprovalService.ActionResult.error("Suspension failed. Please try again.");
        }
    }

    public ApprovalService.ActionResult offboard(UUID userId, ApprovalService.AdminActor admin) {
        try {
            Optional<AcruetUser> userOptional = Database.inTransactionReturning(
                    connection -> userRepository.findById(connection, userId));
            if (userOptional.isEmpty()) {
                return ApprovalService.ActionResult.error("User not found.");
            }
            AcruetUser user = userOptional.get();
            if (isSelf(admin, user)) {
                return ApprovalService.ActionResult.error("You cannot offboard your own account.");
            }
            Optional<UserOffboard> existing = Database.inTransactionReturning(
                    connection -> offboardRepository.findByUserId(connection, userId));
            if (existing.isPresent() && existing.get().isActive()) {
                return ApprovalService.ActionResult.error("Offboarding is already in progress for this user.");
            }
            Instant now = Instant.now();
            Instant deadline = now.plus(OFFBOARD_EXPORT_DAYS, ChronoUnit.DAYS);
            Database.inTransaction(connection -> {
                offboardRepository.insert(
                        connection,
                        userId,
                        deadline,
                        admin.keycloakUserId(),
                        admin.email());
                auditRepository.insert(
                        connection,
                        admin.keycloakUserId(),
                        admin.email(),
                        ApprovalAction.OFFBOARD_USER,
                        TARGET_TYPE_USER,
                        userId,
                        "Export deadline " + DISPLAY_TIME.format(deadline));
            });
            sendOffboardEmail(user, deadline);
            return ApprovalService.ActionResult.success(
                    "Offboarding started for " + user.email() + ". Export window ends "
                            + DISPLAY_TIME.format(deadline)
                            + ".");
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Offboard failed", exception);
            return ApprovalService.ActionResult.error("Offboarding failed. Please try again.");
        }
    }

    public ApprovalService.ActionResult unblockSignup(UUID applicationId, ApprovalService.AdminActor admin) {
        try {
            Optional<SignupApplication> applicationOptional = signupRepository.findById(applicationId);
            if (applicationOptional.isEmpty()) {
                return ApprovalService.ActionResult.error("Application not found.");
            }
            SignupApplication application = applicationOptional.get();
            Database.inTransaction(connection -> {
                if (!signupRepository.unblock(connection, applicationId)) {
                    throw new IllegalStateException("Application is not blocked.");
                }
                auditRepository.insert(
                        connection,
                        admin.keycloakUserId(),
                        admin.email(),
                        ApprovalAction.UNBLOCK_SIGNUP,
                        TARGET_TYPE_SIGNUP,
                        applicationId,
                        "Unblocked " + application.email());
            });
            return ApprovalService.ActionResult.success("Unblocked " + application.email() + " for re-application.");
        } catch (IllegalStateException exception) {
            return ApprovalService.ActionResult.error(exception.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Unblock failed", exception);
            return ApprovalService.ActionResult.error("Unblock failed. Please try again.");
        }
    }

    public CronResult runAutoUnsuspend() {
        Instant now = Instant.now();
        int restored = 0;
        try {
            for (AcruetUser user : userRepository.listSuspendedDue(now)) {
                try {
                    keycloakAdminClient.setUserEnabled(user.keycloakUserId(), true);
                    Database.inTransaction(connection -> {
                        userRepository.clearSuspension(connection, user.id());
                        auditRepository.insert(
                                connection,
                                CRON_ACTOR.keycloakUserId(),
                                CRON_ACTOR.email(),
                                ApprovalAction.UNSUSPEND_USER,
                                TARGET_TYPE_USER,
                                user.id(),
                                "Auto-unsuspended after " + DISPLAY_TIME.format(now));
                    });
                    restored++;
                } catch (Exception exception) {
                    LOGGER.log(
                            Level.WARNING,
                            "Auto-unsuspend failed for user " + user.email(),
                            exception);
                }
            }
            return new CronResult(restored, List.of());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Auto-unsuspend job failed", exception);
            return new CronResult(restored, List.of(exception.getMessage()));
        }
    }

    public CronResult runOffboardPurge() {
        Instant now = Instant.now();
        int purged = 0;
        List<String> errors = new ArrayList<>();
        try {
            for (UserOffboard offboard : offboardRepository.listDueForPurge(now)) {
                try {
                    Optional<AcruetUser> userOptional = Database.inTransactionReturning(
                            connection -> userRepository.findById(connection, offboard.userId()));
                    if (userOptional.isEmpty()) {
                        continue;
                    }
                    AcruetUser user = userOptional.get();
                    keycloakAdminClient.setUserEnabled(user.keycloakUserId(), false);
                    Database.inTransaction(connection -> {
                        auditRepository.insert(
                                connection,
                                CRON_ACTOR.keycloakUserId(),
                                CRON_ACTOR.email(),
                                ApprovalAction.PURGE_USER,
                                TARGET_TYPE_USER,
                                offboard.userId(),
                                offboard.isExportComplete()
                                        ? "Purged after export complete"
                                        : "Purged after export deadline");
                        userRepository.deleteById(connection, offboard.userId());
                    });
                    purged++;
                } catch (Exception exception) {
                    LOGGER.log(
                            Level.WARNING,
                            "Offboard purge failed for user id " + offboard.userId(),
                            exception);
                    errors.add(offboard.userId() + ": " + exception.getMessage());
                }
            }
            return new CronResult(purged, errors);
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Offboard purge job failed", exception);
            errors.add(exception.getMessage());
            return new CronResult(purged, errors);
        }
    }

    public void alertLoginAnomaly(long anomalyId) {
        if (alertEmails.isEmpty()) {
            return;
        }
        try {
            Optional<LoginAnomalyRepository.LoginAnomaly> anomalyOptional =
                    anomalyRepository.findById(anomalyId);
            if (anomalyOptional.isEmpty()) {
                return;
            }
            LoginAnomalyRepository.LoginAnomaly anomaly = anomalyOptional.get();
            if (anomaly.alertedAt() != null) {
                return;
            }
            String subject = "a-cruet login anomaly: unlinked Keycloak session";
            String body = """
                    An unlinked Keycloak login was recorded on the user app.

                    Keycloak subject: %s
                    Email: %s
                    Time: %s
                    Detail: %s

                    Review the anomaly queue: %s/anomalies
                    """
                    .formatted(
                            anomaly.keycloakUserId(),
                            anomaly.email() == null ? "(unknown)" : anomaly.email(),
                            DISPLAY_TIME.format(anomaly.createdAt()),
                            anomaly.detail() == null ? "" : anomaly.detail(),
                            adminBaseUrl);
            Instant alertedAt = Instant.now();
            boolean sent = false;
            for (String alertEmail : alertEmails) {
                try {
                    mailSender.send(alertEmail, subject, body);
                    sent = true;
                } catch (MessagingException exception) {
                    LOGGER.log(Level.WARNING, "Login anomaly alert failed for " + alertEmail, exception);
                }
            }
            if (sent) {
                Database.inTransaction(connection -> {
                    anomalyRepository.markAlerted(connection, anomalyId, alertedAt);
                    auditRepository.insert(
                            connection,
                            "system:alert",
                            null,
                            ApprovalAction.ALERT_LOGIN_ANOMALY,
                            TARGET_TYPE_ANOMALY,
                            UUID.nameUUIDFromBytes(("anomaly-" + anomalyId).getBytes()),
                            "Alerted " + String.join(", ", alertEmails));
                });
            }
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Login anomaly alert failed", exception);
        }
    }

    public int deliverPendingAnomalyAlerts() {
        int delivered = 0;
        try {
            for (LoginAnomalyRepository.LoginAnomaly anomaly : anomalyRepository.listUnalerted()) {
                alertLoginAnomaly(anomaly.id());
                delivered++;
            }
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Pending anomaly alert sweep failed", exception);
        }
        return delivered;
    }

    private ApprovalService.ActionResult changeAdminRole(
            UUID userId, ApprovalService.AdminActor admin, boolean grant) {
        try {
            Optional<AcruetUser> userOptional = Database.inTransactionReturning(
                    connection -> userRepository.findById(connection, userId));
            if (userOptional.isEmpty()) {
                return ApprovalService.ActionResult.error("User not found.");
            }
            AcruetUser user = userOptional.get();
            if (isSelf(admin, user)) {
                return ApprovalService.ActionResult.error("You cannot change your own administrator role here.");
            }
            if (grant) {
                keycloakAdminClient.grantRealmRole(user.keycloakUserId(), adminRole);
            } else {
                keycloakAdminClient.revokeRealmRole(user.keycloakUserId(), adminRole);
            }
            Database.inTransaction(connection -> auditRepository.insert(
                    connection,
                    admin.keycloakUserId(),
                    admin.email(),
                    grant ? ApprovalAction.GRANT_ADMIN : ApprovalAction.REVOKE_ADMIN,
                    TARGET_TYPE_USER,
                    userId,
                    adminRole));
            return ApprovalService.ActionResult.success(
                    (grant ? "Granted " : "Revoked ") + adminRole + " for " + user.email() + ".");
        } catch (KeycloakAdminException exception) {
            LOGGER.log(Level.WARNING, "Admin role change failed in Keycloak", exception);
            return ApprovalService.ActionResult.error(exception.getMessage());
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Admin role change failed", exception);
            return ApprovalService.ActionResult.error("Administrator role change failed.");
        }
    }

    private void sendSuspendEmail(AcruetUser user, int days, Instant until) {
        try {
            mailSender.send(
                    user.email(),
                    "Your a-cruet account has been suspended",
                    """
                    Hello %s,

                    Your a-cruet account has been suspended for %d day(s).

                    You will not be able to sign in until %s.

                    If you believe this is an error, contact an administrator.
                    """
                            .formatted(user.displayName(), days, DISPLAY_TIME.format(until)));
        } catch (MessagingException exception) {
            LOGGER.log(Level.WARNING, "Suspend email failed for " + user.email(), exception);
        }
    }

    private void sendOffboardEmail(AcruetUser user, Instant deadline) {
        try {
            mailSender.send(
                    user.email(),
                    "Your a-cruet account is being offboarded",
                    """
                    Hello %s,

                    An administrator has started offboarding your a-cruet account.

                    Sign in at %s and open the data export page before %s to download a decrypted
                    copy of your ledger (CSV and JSON). After you confirm export or when the window
                    ends, your a-cruet data will be permanently deleted and your login disabled.

                    Export page: %s/offboard
                    """
                            .formatted(
                                    user.displayName(),
                                    userBaseUrl,
                                    DISPLAY_TIME.format(deadline),
                                    userBaseUrl));
        } catch (MessagingException exception) {
            LOGGER.log(Level.WARNING, "Offboard email failed for " + user.email(), exception);
        }
    }

    private static boolean isSelf(ApprovalService.AdminActor admin, AcruetUser user) {
        return admin.keycloakUserId().equals(user.keycloakUserId());
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

    public record OperationalUserView(UserRepository.OperationalUserRow row, boolean adminRoleGranted) {
    }

    public record CronResult(int processed, List<String> errors) {
    }
}
