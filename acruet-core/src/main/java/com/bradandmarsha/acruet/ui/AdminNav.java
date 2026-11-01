package com.bradandmarsha.acruet.ui;

import com.bradandmarsha.acruet.auth.OidcUser;

/**
 * Top navigation chrome for authenticated administrator pages.
 */
public final class AdminNav {

    /** Bump when static JS changes so browsers reload cached scripts. */
    public static final String STATIC_ASSET_VERSION = "20261101-7";

    private AdminNav() {
    }

    public static String authNavHtml(OidcUser user) {
        String initials = UserInitials.from(user);
        String displayName = UserInitials.displayName(null, user);
        String email = user.email() == null ? "" : user.email();
        return """
                <nav class="top-nav user-nav" id="adminNav" aria-label="Account"
                     data-initials="%s"
                     data-name="%s"
                     data-email="%s">
                  <div class="user-menu">
                    <button type="button" id="avatarBtn" class="avatar-btn"
                            aria-expanded="false" aria-haspopup="true">%s</button>
                    <div id="avatarMenu" class="avatar-menu" hidden></div>
                  </div>
                </nav>
                """
                .formatted(
                        escapeAttr(initials),
                        escapeAttr(displayName),
                        escapeAttr(email),
                        escapeText(initials));
    }

    public static String authNavScripts() {
        return "<script src=\"/static/js/acruet-admin-nav.js?v=" + STATIC_ASSET_VERSION + "\"></script>";
    }

    private static String escapeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeAttr(String value) {
        return escapeText(value)
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
