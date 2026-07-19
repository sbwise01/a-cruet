package com.bradandmarsha.acruet.ui;

/**
 * Top navigation chrome for public and authenticated user pages (Phase 9).
 */
public final class UserNav {

    /** Bump when static JS changes so browsers reload cached scripts. */
    public static final String STATIC_ASSET_VERSION = "20260719-4";

    private UserNav() {
    }

    public static String publicNavHtml() {
        return """
                <nav class="top-nav public-nav" aria-label="Account">
                  <a class="nav-btn" href="/signup">Sign up</a>
                  <a class="nav-btn" href="/auth/login">Sign in</a>
                </nav>
                """;
    }

    public static String authNavHtml(AuthNavContext context) {
        return """
                <nav class="top-nav user-nav" id="userNav" aria-label="Account"
                     data-initials="%s"
                     data-name="%s"
                     data-email="%s"
                     data-account-linked="%s"
                     data-key-setup="%s"
                     data-inline-unlock="%s">
                  <div class="user-menu">
                    <button type="button" id="avatarBtn" class="avatar-btn"
                            aria-expanded="false" aria-haspopup="true">%s</button>
                    <div id="avatarMenu" class="avatar-menu" hidden></div>
                  </div>
                </nav>
                """
                .formatted(
                        escapeAttr(context.initials()),
                        escapeAttr(context.displayName()),
                        escapeAttr(context.email() == null ? "" : context.email()),
                        context.accountLinked(),
                        context.keySetupComplete(),
                        context.inlineUnlockHome(),
                        escapeText(context.initials()));
    }

    public static String authNavScripts() {
        return """
                <script src="/static/js/acruet-crypto.js?v=%s"></script>
                <script src="/static/js/acruet-user-nav.js?v=%s"></script>
                """
                .formatted(STATIC_ASSET_VERSION, STATIC_ASSET_VERSION);
    }

    public static String keyPageScript(String filename) {
        return "<script src=\"/static/js/" + filename + "?v=" + STATIC_ASSET_VERSION + "\"></script>";
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
