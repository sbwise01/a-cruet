package com.bradandmarsha.acruet.ui;

/**
 * Signed-in launcher tiles (envelopes, assets, reports).
 */
public final class HomeViews {

    private HomeViews() {
    }

    public static String homeCss() {
        return """
                .app-tile-grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
                  gap: 1.25rem;
                  max-width: 36rem;
                  margin: 1.5rem auto;
                }
                a.app-tile {
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  gap: 0.75rem;
                  background: var(--bg-card);
                  border: 1px solid rgba(148, 163, 184, 0.12);
                  border-radius: 16px;
                  padding: 1.5rem 1rem;
                  color: var(--text);
                  text-decoration: none;
                  font: inherit;
                  transition: transform 0.15s ease, background 0.15s ease, border-color 0.15s ease;
                }
                a.app-tile:hover {
                  transform: translateY(-4px);
                  background: #334155;
                  border-color: var(--accent);
                }
                a.app-tile img {
                  width: 64px;
                  height: 64px;
                  object-fit: contain;
                  border-radius: 12px;
                  background: #fff;
                  padding: 0.4rem;
                  box-sizing: border-box;
                }
                .app-tile-label { font-weight: 600; text-align: center; line-height: 1.3; }
                """;
    }

    public static String homeMainHtml(
            String bannerHtml, String envelopeImageUrl, String assetsImageUrl, String reportsImageUrl) {
        return (bannerHtml == null ? "" : bannerHtml)
                + """
                <div class="app-tile-grid">
                  <a class="app-tile" href="/envelopes">
                    <img src="%s" alt="Envelopes">
                    <span class="app-tile-label">Envelopes</span>
                  </a>
                  <a class="app-tile" href="/assets">
                    <img src="%s" alt="Assets">
                    <span class="app-tile-label">Assets</span>
                  </a>
                  <a class="app-tile" href="/reports">
                    <img src="%s" alt="Reports">
                    <span class="app-tile-label">Reports</span>
                  </a>
                </div>
                """
                .formatted(
                        escapeAttr(envelopeImageUrl),
                        escapeAttr(assetsImageUrl),
                        escapeAttr(reportsImageUrl));
    }

    private static String escapeAttr(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
