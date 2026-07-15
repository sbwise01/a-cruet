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
                %s
                  <main class="page">
                  %s
                  </main>
                %s
                </body>
                </html>
                """
                .formatted(
                        escapeText(title),
                        PageStyles.baseCss(),
                        layoutCss(),
                        extraCss == null ? "" : extraCss,
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
                  </footer>
                """
                .formatted(escapeText(FOOTER_VERSE), escapeText(FOOTER_CITATION));
    }

    private static String layoutCss() {
        return """
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
