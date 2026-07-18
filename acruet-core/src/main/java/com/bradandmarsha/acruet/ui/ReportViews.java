package com.bradandmarsha.acruet.ui;

/**
 * Reports hub and report runner HTML/CSS (Phase 10).
 */
public final class ReportViews {

    private ReportViews() {
    }

    public static String reportsCss() {
        return PageStyles.tableCss() + """
                .report-tile-grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
                  gap: 1.25rem;
                  max-width: 22rem;
                  margin: 1.5rem auto;
                }
                .report-tile {
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
                  cursor: pointer;
                  font: inherit;
                  transition: transform 0.15s ease, background 0.15s ease, border-color 0.15s ease;
                }
                .report-tile:hover {
                  transform: translateY(-4px);
                  background: #334155;
                  border-color: var(--accent);
                }
                .report-tile img {
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
                """;
    }

    public static String reportsPanelHtml(String transactionsImageUrl, String chartImageUrl) {
        return """
                <section id="reportsPanel" hidden>
                  <div id="reportsHub">
                    <h2>Reports</h2>
                    <p class="hint">Choose a report. Data is decrypted in your browser only.</p>
                    <div class="report-tile-grid">
                      <button type="button" class="report-tile" data-report="transactions">
                        <img src="%s" alt="Transactions">
                        <span class="report-tile-label">Transactions</span>
                      </button>
                      <button type="button" class="report-tile" data-report="chart">
                        <img src="%s" alt="Balance chart">
                        <span class="report-tile-label">Balance chart</span>
                      </button>
                    </div>
                    <p class="report-panel-actions">
                      <button type="button" id="btnReportsBack" class="secondary">Back to envelopes</button>
                    </p>
                  </div>
                  <div id="reportTransactionsPanel" hidden>
                    <h2>Transaction report</h2>
                    <p class="hint">View ledger lines for the selected envelopes and date range.</p>
                    <label for="txReportFrom">From</label>
                    <input id="txReportFrom" type="date" required>
                    <label for="txReportTo">To</label>
                    <input id="txReportTo" type="date" required>
                    <div id="txReportAccounts" class="report-account-list"></div>
                    <p class="report-tx-actions">
                      <button type="button" id="btnShowTxReport">Show report</button>
                      <button type="button" id="btnDownloadTxCsv" class="secondary" disabled>Download CSV</button>
                    </p>
                    <p id="txReportError" class="error" hidden></p>
                    <div id="txReportResults" class="report-table-wrap" hidden></div>
                    <p class="report-panel-actions">
                      <button type="button" id="btnTxReportBack" class="secondary">Back to reports</button>
                    </p>
                  </div>
                  <div id="reportChartPanel" hidden>
                    <h2>Balance over time</h2>
                    <p class="hint">Stacked area chart of envelope balances across the selected date range.</p>
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
                      <button type="button" id="btnChartReportBack" class="secondary">Back to reports</button>
                    </p>
                  </div>
                  <p id="reportError" class="error" hidden></p>
                </section>
                """
                .formatted(escapeAttr(transactionsImageUrl), escapeAttr(chartImageUrl));
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
