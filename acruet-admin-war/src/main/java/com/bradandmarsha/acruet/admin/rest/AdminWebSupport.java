package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.approval.ApprovalService;
import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.ui.AdminPageLayout;
import com.bradandmarsha.acruet.ui.PageStyles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.Context;

import java.util.Optional;

/**
 * Shared helpers for administrator HTML resources.
 */
final class AdminWebSupport {

    private AdminWebSupport() {
    }

    static String flashNotice(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "";
        }
        Object successObject = session.getAttribute("acruet.admin.flash.success");
        Object messageObject = session.getAttribute("acruet.admin.flash.message");
        session.removeAttribute("acruet.admin.flash.success");
        session.removeAttribute("acruet.admin.flash.message");
        if (!(messageObject instanceof String message) || message.isBlank()) {
            return "";
        }
        boolean success = Boolean.TRUE.equals(successObject);
        String cssClass = success ? "success" : "error";
        return "<div class=\"notice " + cssClass + "\">" + escape(message) + "</div>";
    }

    static void setFlash(HttpServletRequest request, boolean success, String message) {
        HttpSession session = request.getSession(true);
        session.setAttribute("acruet.admin.flash.success", success);
        session.setAttribute("acruet.admin.flash.message", message);
    }

    static Optional<OidcUser> currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object userObject = session.getAttribute(OidcUser.SESSION_ATTRIBUTE);
        if (userObject instanceof OidcUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    static Optional<ApprovalService.AdminActor> currentActor(HttpServletRequest request) {
        return currentUser(request)
                .map(user -> new ApprovalService.AdminActor(user.subject(), user.email()));
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String adminShell(String title, String notice, String body) {
        return AdminPageLayout.render(
                title,
                PageStyles.tableCss() + PageStyles.adminWideCss(),
                notice + body + "<p><a href=\"/\">Back to dashboard</a></p>");
    }
}
