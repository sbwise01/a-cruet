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
}
