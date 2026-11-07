package com.bradandmarsha.acruet.ui;

/**
 * Snapshot assets and liabilities page (PRODUCT.md §9).
 */
public final class AssetViews {

    private AssetViews() {
    }

    public static String assetsCss() {
        return LedgerViews.ledgerCss()
                + """
                a.account-row {
                  color: inherit;
                  text-decoration: none;
                  cursor: pointer;
                }
                a.account-row:hover { background: rgba(148, 163, 184, 0.08); }
                #assetsList .account-row,
                #liabilitiesList .account-row {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) 4.75rem 7.75rem;
                  column-gap: 0.5rem;
                  align-items: center;
                  justify-content: stretch;
                }
                #assetsList.has-expand .account-row,
                #liabilitiesList.has-expand .account-row {
                  grid-template-columns: 1.5rem minmax(0, 1fr) 4.75rem 7.75rem;
                }
                #assetsList.has-groups .account-row,
                #liabilitiesList.has-groups .account-row {
                  grid-template-columns: 1.5rem minmax(0, 1fr) 2rem 4.75rem 7.75rem;
                }
                #assetsList.has-investments .account-row {
                  grid-template-columns: minmax(0, 1fr) 2rem 4.75rem 7.75rem;
                }
                #assetsList.has-investments.has-expand .account-row {
                  grid-template-columns: 1.5rem minmax(0, 1fr) 2rem 4.75rem 7.75rem;
                }
                #assetsList.has-investments.has-groups .account-row {
                  grid-template-columns: 1.5rem minmax(0, 1fr) 2rem 2rem 4.75rem 7.75rem;
                }
                #assetsList .row-archive,
                #liabilitiesList .row-archive {
                  display: flex;
                  justify-content: center;
                }
                #assetsList .archive-btn,
                #liabilitiesList .archive-btn {
                  margin-left: 0;
                  margin-top: 0;
                }
                #assetsList .balance,
                #liabilitiesList .balance {
                  width: 100%;
                  text-align: right;
                  font-variant-numeric: tabular-nums;
                }
                .register-heading {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) 7.75rem;
                  column-gap: 0.5rem;
                  align-items: baseline;
                  margin: 0;
                  padding: 0 0 0.65rem;
                  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                }
                .register-heading .name { min-width: 0; }
                .register-heading .balance {
                  width: 100%;
                  text-align: right;
                  font: inherit;
                  font-variant-numeric: tabular-nums;
                  white-space: nowrap;
                }
                .position-amount {
                  display: block;
                  width: 100%;
                  font: inherit;
                  font-variant-numeric: tabular-nums;
                  text-align: right;
                  color: inherit;
                  background: transparent;
                  border: none;
                  padding: 0.1rem 0;
                  margin: 0;
                  border-radius: 4px;
                  cursor: pointer;
                }
                .position-amount:hover { background: rgba(148, 163, 184, 0.15); }
                .register-section { margin-bottom: 2rem; }
                .register-meta { margin: 0 0 0.75rem; }
                #registerTotals .net-row-actions {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 0.4rem;
                  margin-top: 0.75rem;
                }
                #registerTotals .net-row-actions .secondary {
                  margin: 0;
                  padding: 0.25rem 0.65rem;
                  font-size: 0.85rem;
                  text-decoration: none;
                }
                #btnSaveNetWorth:disabled {
                  opacity: 0.45;
                  cursor: not-allowed;
                  filter: none;
                }
                #saveNetWorthNotice {
                  margin: 0.5rem 0 0;
                }
                #saveNetWorthNotice.success,
                #historicalNotice.success {
                  color: #34d399;
                }
                a.secondary {
                  display: inline-block;
                  background: #475569;
                  color: var(--text);
                  text-decoration: none;
                }
                .ledger-actions button:disabled {
                  opacity: 0.45;
                  cursor: not-allowed;
                  filter: none;
                }
                .account-row.group-heading {
                  font-size: 0.95rem;
                }
                .account-row .name { min-width: 0; }
                .row-expand,
                .row-group,
                .row-invest {
                  display: flex;
                  justify-content: center;
                  align-items: center;
                }
                .group-expand {
                  width: 1.5rem;
                  height: 1.5rem;
                  padding: 0;
                  margin: 0;
                  background: transparent;
                  color: inherit;
                  border: none;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                .group-expand::before {
                  content: "";
                  width: 0;
                  height: 0;
                  border-style: solid;
                  border-width: 0.28rem 0 0.28rem 0.42rem;
                  border-color: transparent transparent transparent currentColor;
                }
                .group-expand[aria-expanded="true"]::before {
                  border-width: 0.42rem 0.28rem 0 0.28rem;
                  border-color: currentColor transparent transparent transparent;
                }
                .account-row.grouped-item .name {
                  font-weight: 500;
                }
                .group-btn {
                  width: 1.55rem;
                  height: 1.55rem;
                  padding: 0;
                  margin: 0;
                  border-radius: 999px;
                  font-size: 0.7rem;
                  font-weight: 700;
                  letter-spacing: 0;
                  line-height: 1;
                  background: #475569;
                  color: var(--text);
                }
                .group-btn.assigned,
                .invest-btn.assigned {
                  background: var(--accent);
                  color: #0f172a;
                }
                .invest-btn {
                  width: 1.55rem;
                  height: 1.55rem;
                  padding: 0;
                  margin: 0;
                  border-radius: 999px;
                  font-size: 0.7rem;
                  font-weight: 700;
                  letter-spacing: 0;
                  line-height: 1;
                  background: #475569;
                  color: var(--text);
                }
                .group-btn[aria-expanded="true"] {
                  filter: brightness(1.08);
                }
                .group-picker {
                  position: fixed;
                  z-index: 40;
                  min-width: 11rem;
                  max-height: 16rem;
                  overflow: auto;
                  padding: 0.35rem 0;
                  background: var(--bg-card);
                  border: 1px solid rgba(148, 163, 184, 0.2);
                  border-radius: 12px;
                  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
                }
                .group-picker button {
                  display: block;
                  width: 100%;
                  margin: 0;
                  padding: 0.45rem 0.9rem;
                  text-align: left;
                  background: transparent;
                  color: var(--text);
                  border: none;
                  border-radius: 0;
                  font: inherit;
                  font-weight: 500;
                  cursor: pointer;
                }
                .group-picker button:hover {
                  background: rgba(56, 189, 248, 0.08);
                  filter: none;
                }
                .group-picker button[aria-selected="true"] {
                  color: var(--accent);
                  font-weight: 600;
                }
                .group-heading .balance {
                  font-weight: 600;
                }
                """;
    }

    public static String assetsMainHtml(String bannerHtml, String lockImageUrl) {
        return (bannerHtml == null ? "" : bannerHtml)
                + """
                <div id="assetsRoot" data-lock-image="%s">
                  <div id="assetsLocked" hidden>
                    <div class="unlock-tile-wrap">
                      <button type="button" id="unlockTile" class="unlock-tile">
                        <img src="%s" alt="Locked">
                        <span class="unlock-tile-label">Unlock assets</span>
                      </button>
                    </div>
                    """.formatted(escapeAttr(lockImageUrl), escapeAttr(lockImageUrl))
                + LedgerViews.inlineUnlockFormHtml()
                + """
                  </div>
                  <div id="assetsBrowse">
                    <div id="assetsError" class="error" hidden></div>
                    <p id="registerMeta" class="hint register-meta" hidden></p>
                    <div class="ledger-actions">
                      <button type="button" class="ledger-action-btn" data-action="create-asset">Add asset</button>
                      <button type="button" class="ledger-action-btn" data-action="create-liability">Add liability</button>
                      <button type="button" class="ledger-action-btn" data-action="create-group">Add group</button>
                    </div>
                    <section id="assetsSection" class="register-section">
                      <h2 class="register-heading">
                        <span class="name">Assets</span>
                        <span class="balance" id="assetsTotal"></span>
                      </h2>
                      <div id="assetsList"><p class="hint">Loading…</p></div>
                    </section>
                    <section id="liabilitiesSection" class="register-section">
                      <h2 class="register-heading">
                        <span class="name">Liabilities</span>
                        <span class="balance" id="liabilitiesTotal"></span>
                      </h2>
                      <div id="liabilitiesList"><p class="hint">Loading…</p></div>
                    </section>
                    <section id="registerTotals" class="register-section" hidden>
                      <h2 class="register-heading">
                        <span class="name">Net</span>
                        <span class="balance" id="netTotal"></span>
                      </h2>
                      <div class="net-row-actions">
                        <a href="/assets/historical-net-worth" class="secondary">Add historical total</a>
                        <button type="button" id="btnSaveNetWorth" class="secondary">Save net worth</button>
                      </div>
                    </section>
                    <p id="saveNetWorthNotice" class="hint" hidden></p>
                  </div>
                  <section id="formPanel" hidden>
                    <h2 id="formTitle"></h2>
                    <div id="formError" class="form-error" hidden></div>
                    <div id="formBody"></div>
                    <p>
                      <button type="button" id="btnSubmitForm">Save</button>
                      <button type="button" id="btnCancelForm" class="secondary">Cancel</button>
                    </p>
                  </section>
                </div>
                """
                + UserNav.keyPageScript("acruet-net-worth.js")
                + UserNav.keyPageScript("acruet-assets.js");
    }

    public static String historicalNetWorthHtml(String bannerHtml, String lockImageUrl) {
        return (bannerHtml == null ? "" : bannerHtml)
                + """
                <div id="historicalNetWorthRoot">
                  <div id="historicalLocked" hidden>
                    <div class="unlock-tile-wrap">
                      <button type="button" id="unlockTile" class="unlock-tile">
                        <img src="%s" alt="Locked">
                        <span class="unlock-tile-label">Unlock assets</span>
                      </button>
                    </div>
                    """.formatted(escapeAttr(lockImageUrl))
                + LedgerViews.inlineUnlockFormHtml()
                + """
                  </div>
                  <div id="historicalBrowse">
                    <h2>Add historical total</h2>
                    <p class="hint">Use this for year-end totals you already have. One date and one amount — no line items are invented.</p>
                    <p id="historicalError" class="error" hidden></p>
                    <p id="historicalNotice" class="hint" hidden></p>
                    <label for="netWorthAsOf">As of</label>
                    <input id="netWorthAsOf" type="date" required>
                    <label for="netWorthAmount">Net worth</label>
                    <input id="netWorthAmount" type="text" inputmode="decimal" required placeholder="0.00">
                    <p>
                      <button type="button" id="btnAddHistoricalNetWorth">Add historical total</button>
                      <a href="/assets" class="secondary">Back to assets</a>
                    </p>
                    <p class="hint"><a href="/reports/net-worth">View net worth report</a></p>
                  </div>
                </div>
                """
                + UserNav.keyPageScript("acruet-net-worth.js")
                + UserNav.keyPageScript("acruet-net-worth-historical.js");
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
