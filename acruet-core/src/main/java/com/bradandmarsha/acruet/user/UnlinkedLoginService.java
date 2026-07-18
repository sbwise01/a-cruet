package com.bradandmarsha.acruet.user;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.db.Database;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records Keycloak sessions that have no matching {@code acruet_user} row (Phase 9 item 10).
 */
public final class UnlinkedLoginService {

    private static final Logger LOGGER = Logger.getLogger(UnlinkedLoginService.class.getName());
    static final String SESSION_RECORDED_ATTRIBUTE = "acruet.unlinked.login.recorded";

    private final LoginAnomalyRepository repository = new LoginAnomalyRepository();

    public void recordIfNeeded(HttpServletRequest request, OidcUser oidcUser) {
        HttpSession session = request.getSession(false);
        if (session == null || Boolean.TRUE.equals(session.getAttribute(SESSION_RECORDED_ATTRIBUTE))) {
            return;
        }
        try {
            Database.inTransaction(connection -> {
                repository.insert(
                        connection,
                        oidcUser.subject(),
                        oidcUser.email(),
                        "Keycloak session without matching acruet_user");
            });
            session.setAttribute(SESSION_RECORDED_ATTRIBUTE, Boolean.TRUE);
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Failed to record unlinked login anomaly", exception);
        }
    }
}
