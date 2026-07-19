package com.bradandmarsha.acruet.user;

import com.bradandmarsha.acruet.admin.AdminOpsService;
import com.bradandmarsha.acruet.db.Database;

/**
 * Records unlinked Keycloak login events and alerts administrators (Phase 9 item 10, Phase 11).
 */
public final class UnlinkedLoginService {

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(UnlinkedLoginService.class.getName());
    static final String SESSION_RECORDED_ATTRIBUTE = "acruet.unlinked.login.recorded";

    private final LoginAnomalyRepository repository = new LoginAnomalyRepository();
    private final AdminOpsService adminOpsService = AdminOpsService.fromEnvironment();

    public void recordIfNeeded(jakarta.servlet.http.HttpServletRequest request, com.bradandmarsha.acruet.auth.OidcUser oidcUser) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null || Boolean.TRUE.equals(session.getAttribute(SESSION_RECORDED_ATTRIBUTE))) {
            return;
        }
        try {
            long anomalyId = Database.inTransactionReturning(connection -> repository.insertReturningId(
                    connection,
                    oidcUser.subject(),
                    oidcUser.email(),
                    "Keycloak session without matching acruet_user"));
            session.setAttribute(SESSION_RECORDED_ATTRIBUTE, Boolean.TRUE);
            adminOpsService.alertLoginAnomaly(anomalyId);
        } catch (java.sql.SQLException exception) {
            LOGGER.log(java.util.logging.Level.WARNING, "Failed to record unlinked login anomaly", exception);
        }
    }
}
