package com.bradandmarsha.acruet.ui;

/**
 * Reports hub and dedicated report pages (Phase 10 + PRODUCT.md §11).
 */
public final class ReportViews {

    public enum ReportPage {
        HUB("hub", "Reports"),
        ENVELOPE_TRANSACTIONS("transactions", "Envelope transactions"),
        ENVELOPE_BALANCE("chart", "Envelope balance chart"),
        NET_WORTH("net-worth", "Net worth");

        private final String dataReport;
        private final String title;

        ReportPage(String dataReport, String title) {
            this.dataReport = dataReport;
            this.title = title;
        }

        public String dataReport() {
            return dataReport;
        }

        public String title() {
            return title;
        }
    }

    private ReportViews() {
    }

    public static String reportsCss() {
        return PageStyles.tableCss() + """
                .report-tile-grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
                  gap: 1.25rem;
                  max-width: 34rem;
                  margin: 1.5rem auto;
                }
                a.report-tile {
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
                a.report-tile:hover {
                  transform: translateY(-4px);
                  background: #334155;
                  border-color: var(--accent);
                }
                a.report-tile img {
                  width: 64px;
                  height: 64px;
                  object-fit: contain;
                  border-radius: 12px;
                  background: #fff;
                  padding: 0.4rem;
                  box-sizing: border-box;
                }
                .report-tile-label { font-weight: 600; text-align: center; line-height: 1.3; }
                .report-panel-actions { margin-top: 1.25rem; }
                a.secondary {
                  display: inline-block;
                  margin-top: 1.25rem;
                  padding: 0.6rem 1.2rem;
                  font: inherit;
                  font-weight: 600;
                  color: var(--text);
                  background: #475569;
                  border: none;
                  border-radius: 8px;
                  text-decoration: none;
                }
                a.secondary:hover { filter: brightness(1.05); }
                .report-account-list {
                  margin: 0.75rem 0 1rem;
                  padding: 0.75rem 1rem;
                  border-radius: 8px;
                  background: var(--bg-card);
                  border: 1px solid rgba(148, 163, 184, 0.15);
                }
                .report-account-list label {
                  display: flex;
                  align-items: center;
                  gap: 0.5rem;
                  margin-top: 0.35rem;
                  font-weight: 500;
                }
                .report-account-list label:first-child { margin-top: 0; }
                .report-account-list input { width: auto; margin: 0; }
                .report-chart-wrap {
                  position: relative;
                  height: 640px;
                  margin-top: 1.25rem;
                }
                main.page.page--report-chart {
                  max-width: 80rem;
                }
                #reportError {
                  margin-top: 1rem;
                }
                #txReportError {
                  margin-top: 0.75rem;
                  padding: 0.75rem 1rem;
                  border-radius: 8px;
                  background: rgba(248, 113, 113, 0.12);
                  border-left: 3px solid #f87171;
                }
                .report-table-wrap {
                  margin-top: 1.25rem;
                  overflow-x: auto;
                }
                .report-table-wrap .hint {
                  margin-top: 0;
                }
                .report-tx-actions {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 0.5rem;
                  margin-top: 0.25rem;
                }
                .report-tx-actions button {
                  margin-top: 0;
                }
                .report-tx-actions button + button {
                  margin-left: 0;
                }
                .report-tx-actions button.secondary:disabled {
                  opacity: 0.45;
                  cursor: not-allowed;
                  filter: none;
                }
                .report-amount-negative { color: #fbbf24; }
                #netWorthHistorical { margin-top: 1.75rem; }
                #netWorthHistorical h3 { margin-bottom: 0.35rem; }
                #netWorthFormError { margin-top: 0.75rem; }
                """;
    }

    public static String reportsPageHtml(
            String bannerHtml,
            String lockImageUrl,
            ReportPage page,
            String transactionsImageUrl,
            String chartImageUrl,
            String netWorthImageUrl) {
        ReportPage kind = page == null ? ReportPage.HUB : page;
        return (bannerHtml == null ? "" : bannerHtml)
                + """
                <div id="reportsRoot" data-report="%s">
                  <div id="reportsLocked" hidden>
                    <div class="unlock-tile-wrap">
                      <button type="button" id="unlockTile" class="unlock-tile">
                        <img src="%s" alt="Locked">
                        <span class="unlock-tile-label">Unlock reports</span>
                      </button>
                    </div>
                    """.formatted(escapeAttr(kind.dataReport()), escapeAttr(lockImageUrl))
                + LedgerViews.inlineUnlockFormHtml()
                + """
                  </div>
                  <div id="reportsBrowse">
                    <div id="reportsPageError" class="error" hidden></div>
                    %s
                    %s
                  </div>
                </div>
                """.formatted(bundlePanelHtml(kind), pageBodyHtml(kind, transactionsImageUrl, chartImageUrl, netWorthImageUrl))
                + pageScripts(kind);
    }

    private static String bundlePanelHtml(ReportPage page) {
        if (page != ReportPage.ENVELOPE_TRANSACTIONS && page != ReportPage.ENVELOPE_BALANCE) {
            return "";
        }
        return """
                <section id="bundlePanel">
                  <h2>Bundle</h2>
                  <div class="bundle-bar">
                    <select id="bundleSelect" aria-label="Current bundle"></select>
                  </div>
                </section>
                """;
    }

    private static String pageBodyHtml(
            ReportPage page,
            String transactionsImageUrl,
            String chartImageUrl,
            String netWorthImageUrl) {
        return switch (page) {
            case HUB -> hubHtml(transactionsImageUrl, chartImageUrl, netWorthImageUrl);
            case ENVELOPE_TRANSACTIONS -> envelopeTransactionsHtml();
            case ENVELOPE_BALANCE -> envelopeBalanceHtml();
            case NET_WORTH -> netWorthHtml();
        };
    }

    private static String hubHtml(
            String transactionsImageUrl, String chartImageUrl, String netWorthImageUrl) {
        return """
                <section id="reportsPanel">
                  <div id="reportsHub">
                    <h2>Reports</h2>
                    <p class="hint">Choose a report. Data is decrypted in your browser only.</p>
                    <div class="report-tile-grid">
                      <a class="report-tile" href="/reports/envelope-transactions">
                        <img src="%s" alt="Envelope transactions">
                        <span class="report-tile-label">Envelope transactions</span>
                      </a>
                      <a class="report-tile" href="/reports/envelope-balance">
                        <img src="%s" alt="Envelope balance chart">
                        <span class="report-tile-label">Envelope balance chart</span>
                      </a>
                      <a class="report-tile" href="/reports/net-worth">
                        <img src="%s" alt="Net worth">
                        <span class="report-tile-label">Net worth</span>
                      </a>
                    </div>
                  </div>
                </section>
                """
                .formatted(
                        escapeAttr(transactionsImageUrl),
                        escapeAttr(chartImageUrl),
                        escapeAttr(netWorthImageUrl));
    }

    private static String envelopeTransactionsHtml() {
        return """
                <section id="reportsPanel">
                  <div id="reportTransactionsPanel">
                    <h2>Envelope transaction report</h2>
                    <p class="hint">View ledger lines on screen or download CSV for the same filters.</p>
                    <p class="hint reports-bundle-hint"></p>
                    <label for="txReportFrom">From</label>
                    <input id="txReportFrom" type="date" required>
                    <label for="txReportTo">To</label>
                    <input id="txReportTo" type="date" required>
                    <div id="txReportAccounts" class="report-account-list"></div>
                    <div class="report-tx-actions">
                      <button type="button" id="btnShowTxReport"
                              onclick="return window.AcruetReports &amp;&amp; window.AcruetReports.showTxReportClick(event)">Show report</button>
                      <button type="button" id="btnDownloadTxCsv" class="secondary"
                              onclick="return window.AcruetReports &amp;&amp; window.AcruetReports.downloadTxCsvClick(event)">Download CSV</button>
                    </div>
                    <p id="txReportError" class="error" hidden></p>
                    <div id="txReportResults" class="report-table-wrap" hidden></div>
                    <p class="report-panel-actions">
                      <a href="/reports" class="secondary">Back to reports</a>
                    </p>
                  </div>
                  <p id="reportError" class="error" hidden></p>
                </section>
                """;
    }

    private static String envelopeBalanceHtml() {
        return """
                <section id="reportsPanel">
                  <div id="reportChartPanel">
                    <h2>Envelope balance over time</h2>
                    <p class="hint">Stacked area chart of envelope balances across the selected date range.</p>
                    <p class="hint reports-bundle-hint"></p>
                    <label for="chartReportFrom">From</label>
                    <input id="chartReportFrom" type="date" required>
                    <label for="chartReportTo">To</label>
                    <input id="chartReportTo" type="date" required>
                    <div id="chartReportAccounts" class="report-account-list"></div>
                    <button type="button" id="btnRenderChart">Show chart</button>
                    <div class="report-chart-wrap">
                      <canvas id="balanceChart"></canvas>
                    </div>
                    <p class="report-panel-actions">
                      <a href="/reports" class="secondary">Back to reports</a>
                    </p>
                  </div>
                  <p id="reportError" class="error" hidden></p>
                </section>
                """;
    }

    private static String netWorthHtml() {
        return """
                <section id="reportsPanel">
                  <div id="reportNetWorthPanel">
                    <h2>Net worth</h2>
                    <p class="hint">Saved observations only. The chart plots the points you stored — months between yearly totals are not filled in.</p>
                    <p id="netWorthMeta" class="hint"></p>
                    <div class="report-chart-wrap">
                      <canvas id="netWorthChart"></canvas>
                    </div>
                    <div id="netWorthTable" class="report-table-wrap"></div>
                    <section id="netWorthHistorical">
                      <h3>Add historical total</h3>
                      <p class="hint">Use this for year-end totals you already have. One date and one amount — no line items are invented.</p>
                      <label for="netWorthAsOf">As of</label>
                      <input id="netWorthAsOf" type="date" required>
                      <label for="netWorthAmount">Net worth</label>
                      <input id="netWorthAmount" type="text" inputmode="decimal" required placeholder="0.00">
                      <p>
                        <button type="button" id="btnAddHistoricalNetWorth">Add historical total</button>
                      </p>
                      <p id="netWorthFormError" class="error" hidden></p>
                    </section>
                    <p class="report-panel-actions">
                      <a href="/reports" class="secondary">Back to reports</a>
                    </p>
                  </div>
                  <p id="reportError" class="error" hidden></p>
                </section>
                """;
    }

    private static String pageScripts(ReportPage page) {
        return switch (page) {
            case HUB -> UserNav.keyPageScript("acruet-reports-page.js");
            case ENVELOPE_TRANSACTIONS -> UserNav.keyPageScript("acruet-reports.js")
                    + UserNav.keyPageScript("acruet-reports-page.js");
            case ENVELOPE_BALANCE -> UserNav.keyPageScript("chart.umd.min.js")
                    + UserNav.keyPageScript("acruet-reports.js")
                    + UserNav.keyPageScript("acruet-reports-page.js");
            case NET_WORTH -> UserNav.keyPageScript("chart.umd.min.js")
                    + UserNav.keyPageScript("acruet-net-worth.js")
                    + UserNav.keyPageScript("acruet-reports.js")
                    + UserNav.keyPageScript("acruet-reports-page.js");
        };
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
