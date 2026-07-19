package com.bradandmarsha.acruet.ui;

/**
 * Shared HTML theme aligned with wise-home-index (homelab dark palette).
 */
public final class PageStyles {

    private PageStyles() {
    }

    /** Background, text, links, and page layout. */
    public static String baseCss() {
        return """
                :root {
                  --bg: #152238;
                  --bg-card: #1e293b;
                  --text: #e2e8f0;
                  --muted: #94a3b8;
                  --accent: #38bdf8;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                  background: var(--bg);
                  color: var(--text);
                  min-height: 100vh;
                  line-height: 1.5;
                }
                .page {
                  max-width: 40rem;
                  margin: 0 auto;
                  padding: 2rem 1.5rem 3rem;
                }
                h1 { margin: 0 0 0.5rem; font-size: 2rem; letter-spacing: -0.5px; }
                h2 { margin: 0 0 0.75rem; font-size: 1.35rem; letter-spacing: -0.25px; }
                p { margin: 0.75rem 0; color: var(--text); }
                a { color: var(--accent); }
                a:hover { text-decoration: none; }
                .hint { color: var(--muted); font-size: 0.95rem; }
                .error { color: #f87171; }
                .actions { margin-top: 1.5rem; }
                .actions a { margin-right: 1rem; }
                """;
    }

    /** Signup form controls on the dark card surface. */
    public static String formCss() {
        return """
                label { display: block; margin-top: 1rem; font-weight: 600; color: var(--text); }
                input, textarea {
                  width: 100%;
                  margin-top: 0.25rem;
                  padding: 0.6rem 0.75rem;
                  font: inherit;
                  color: var(--text);
                  background: var(--bg-card);
                  border: 1px solid rgba(148, 163, 184, 0.2);
                  border-radius: 8px;
                }
                input:focus, textarea:focus {
                  outline: 2px solid rgba(56, 189, 248, 0.35);
                  border-color: var(--accent);
                }
                button {
                  margin-top: 1.25rem;
                  padding: 0.6rem 1.2rem;
                  font: inherit;
                  font-weight: 600;
                  color: var(--bg);
                  background: var(--accent);
                  border: none;
                  border-radius: 8px;
                  cursor: pointer;
                }
                button:hover { filter: brightness(1.05); }
                """;
    }

    /** Admin tables for approval queue and user lists. */
    public static String tableCss() {
        return """
                table {
                  width: 100%;
                  border-collapse: collapse;
                  margin-top: 1rem;
                  font-size: 0.95rem;
                }
                th, td {
                  text-align: left;
                  padding: 0.65rem 0.5rem;
                  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                  vertical-align: top;
                }
                th { color: var(--muted); font-weight: 600; }
                .meta { color: var(--muted); font-size: 0.85rem; }
                .row-actions { white-space: nowrap; }
                .row-actions form { display: inline; margin-right: 0.35rem; }
                .row-actions button {
                  margin-top: 0;
                  padding: 0.35rem 0.75rem;
                  font-size: 0.85rem;
                }
                .row-actions button.reject {
                  background: #475569;
                  color: var(--text);
                }
                .notice {
                  margin-top: 1rem;
                  padding: 0.75rem 1rem;
                  border-radius: 8px;
                  background: var(--bg-card);
                }
                .notice.success { border-left: 3px solid #34d399; }
                .notice.error { border-left: 3px solid #f87171; }
                """;
    }

    /** Wider admin console pages (user list, anomalies). */
    public static String adminWideCss() {
        return """
                .page { max-width: 72rem; }
                .row-actions input[type=number] {
                  width: 4rem;
                  margin-top: 0;
                  padding: 0.35rem 0.5rem;
                }
                .status-tag {
                  display: inline-block;
                  padding: 0.15rem 0.45rem;
                  border-radius: 999px;
                  font-size: 0.8rem;
                  background: rgba(148, 163, 184, 0.15);
                }
                .status-tag.warn { background: rgba(251, 191, 36, 0.15); color: #fcd34d; }
                """;
    }

    /** Upper-right public and authenticated navigation (Phase 9). */
    public static String navCss() {
        return """
                .top-nav {
                  position: absolute;
                  top: 1rem;
                  right: 1rem;
                  z-index: 20;
                  display: flex;
                  align-items: center;
                  gap: 0.5rem;
                }
                .nav-btn, .avatar-btn {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  padding: 0.6rem 1.2rem;
                  font: inherit;
                  font-weight: 600;
                  color: var(--bg);
                  background: var(--accent);
                  border: none;
                  border-radius: 8px;
                  text-decoration: none;
                  cursor: pointer;
                }
                .nav-btn:hover, .avatar-btn:hover { filter: brightness(1.05); text-decoration: none; }
                .avatar-btn {
                  width: 2.75rem;
                  height: 2.75rem;
                  padding: 0;
                  border-radius: 999px;
                  font-size: 0.95rem;
                  letter-spacing: 0.02em;
                }
                .user-menu { position: relative; }
                .avatar-menu {
                  position: absolute;
                  top: calc(100% + 0.5rem);
                  right: 0;
                  min-width: 14rem;
                  padding: 0.75rem 0;
                  background: var(--bg-card);
                  border: 1px solid rgba(148, 163, 184, 0.2);
                  border-radius: 12px;
                  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
                }
                .avatar-menu-identity { padding: 0 1rem 0.65rem; border-bottom: 1px solid rgba(148, 163, 184, 0.15); }
                .avatar-menu-name { font-weight: 600; }
                .avatar-menu-email { font-size: 0.85rem; margin-top: 0.15rem; }
                .avatar-menu-status { padding: 0.65rem 1rem 0.35rem; font-size: 0.9rem; }
                .avatar-menu-actions { padding: 0.25rem 0; }
                .avatar-menu-actions a, .avatar-menu-actions button {
                  display: block;
                  width: 100%;
                  margin: 0;
                  padding: 0.55rem 1rem;
                  text-align: left;
                  background: transparent;
                  color: var(--accent);
                  border: none;
                  border-radius: 0;
                  font: inherit;
                  font-weight: 500;
                  cursor: pointer;
                }
                .avatar-menu-actions a:hover, .avatar-menu-actions button:hover {
                  background: rgba(56, 189, 248, 0.08);
                  filter: none;
                }
                .avatar-menu-signout {
                  display: block;
                  padding: 0.65rem 1rem 0.25rem;
                  border-top: 1px solid rgba(148, 163, 184, 0.15);
                  color: var(--accent);
                  text-decoration: none;
                  font-weight: 600;
                }
                .avatar-menu-signout:hover { text-decoration: none; background: rgba(56, 189, 248, 0.08); }
                @media (max-width: 40rem) {
                  .page-shell:has(.top-nav) .page-header {
                    padding-top: 4.5rem;
                  }
                  .top-nav {
                    top: 0.75rem;
                    right: 0.75rem;
                    left: 0.75rem;
                    justify-content: flex-end;
                  }
                  .top-nav.public-nav {
                    flex-wrap: wrap;
                    gap: 0.35rem;
                  }
                  .nav-btn {
                    padding: 0.5rem 0.85rem;
                    font-size: 0.875rem;
                  }
                }
                """;
    }

    /** Public landing marketing sections (Phase 9 item 3). */
    public static String marketingCss() {
        return """
                .marketing h2 { margin-top: 2rem; }
                .marketing h2:first-child { margin-top: 0; }
                .marketing ul, .marketing ol { margin: 0.75rem 0; padding-left: 1.35rem; }
                .marketing li { margin: 0.35rem 0; }
                """;
    }
}
