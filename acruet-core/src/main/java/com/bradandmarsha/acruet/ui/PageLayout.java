package com.bradandmarsha.acruet.ui;

/**
 * Shared HTML page shell with index-tile header and README footer.
 */
final class PageLayout {

    static final String FOOTER_VERSE =
            "The wise store up choice food and olive oil, but fools gulp theirs down.";
    static final String FOOTER_CITATION = "Proverbs 21:20";

    private PageLayout() {
    }

    static String render(
            String appName,
            String tileImage,
            String tileDescription,
            String title,
            String extraCss,
            String mainHtml) {
        return render(appName, tileImage, tileDescription, title, extraCss, "", mainHtml);
    }

    static String render(
            String appName,
            String tileImage,
            String tileDescription,
            String title,
            String extraCss,
            String topNavHtml,
            String mainHtml) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                %s
                %s
                %s
                  </style>
                </head>
                <body>
                  <div class="page-shell">
                %s
                %s
                    <main class="page">
                %s
                    </main>
                  </div>
                %s
                </body>
                </html>
                """
                .formatted(
                        escapeText(title),
                        PageStyles.baseCss(),
                        layoutCss(),
                        extraCss == null ? "" : extraCss,
                        topNavHtml == null ? "" : topNavHtml,
                        headerHtml(appName, tileImage, tileDescription),
                        mainHtml,
                        footerHtml());
    }

    private static String headerHtml(String appName, String tileImage, String tileDescription) {
        return """
                  <header class="page-header">
                    <img class="tile-image" src="%s" alt="%s logo">
                    <h1>%s</h1>
                    <p class="subtitle">%s</p>
                  </header>
                """
                .formatted(
                        escapeAttr(tileImage),
                        escapeAttr(appName),
                        escapeText(appName),
                        escapeText(tileDescription));
    }

    private static String footerHtml() {
        return """
                  <footer class="page-footer">
                    <p class="verse">&ldquo;%s&rdquo;</p>
                    <p class="citation">&mdash; %s</p>
                    <p class="issues-link"><a href="https://github.com/sbwise01/a-cruet/issues" target="_blank" rel="noopener noreferrer">Report issues or enhancement requests</a></p>
                  </footer>
                """
                .formatted(escapeText(FOOTER_VERSE), escapeText(FOOTER_CITATION));
    }

    private static String layoutCss() {
        return """
                body {
                  display: flex;
                  flex-direction: column;
                }
                .page-shell {
                  flex: 1 0 auto;
                  display: flex;
                  flex-direction: column;
                  position: relative;
                }
                main.page {
                  flex: 1 0 auto;
                }
                .page-header {
                  text-align: center;
                  padding: 2.5rem 1rem 1.5rem;
                }
                .page-header h1 {
                  margin: 0.75rem 0 0.25rem;
                  font-size: 2rem;
                  letter-spacing: -0.5px;
                }
                .subtitle {
                  margin: 0;
                  color: var(--muted);
                }
                .tile-image {
                  width: 64px;
                  height: 64px;
                  object-fit: contain;
                  border-radius: 12px;
                }
                .page-footer {
                  flex-shrink: 0;
                  text-align: center;
                  color: var(--muted);
                  font-size: 0.85rem;
                  padding: 1.5rem 1rem 2.5rem;
                  max-width: 40rem;
                  margin: 0 auto;
                }
                .page-footer .verse {
                  margin: 0;
                  font-style: italic;
                  color: var(--text);
                }
                .page-footer .citation {
                  margin: 0.35rem 0 0;
                }
                .page-footer .issues-link {
                  margin: 1rem 0 0;
                }
                .page-footer .issues-link a {
                  color: var(--accent);
                }
                .page-footer .issues-link a:hover {
                  text-decoration: underline;
                }
                """;
    }

    private static String escapeText(String value) {
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
