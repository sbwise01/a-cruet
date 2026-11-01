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
                #liabilitiesList .account-row,
                #registerTotals .account-row {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) 4.75rem 7.75rem;
                  column-gap: 0.5rem;
                  align-items: center;
                  justify-content: stretch;
                }
                #assetsList .row-archive,
                #liabilitiesList .row-archive,
                #registerTotals .row-archive {
                  display: flex;
                  justify-content: center;
                }
                #assetsList .archive-btn,
                #liabilitiesList .archive-btn {
                  margin-left: 0;
                  margin-top: 0;
                }
                #assetsList .balance,
                #liabilitiesList .balance,
                #registerTotals .balance {
                  width: 100%;
                  text-align: right;
                  font-variant-numeric: tabular-nums;
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
                #registerTotals { margin-top: 0.5rem; }
                #registerTotals .account-row.net-row {
                  grid-template-columns: minmax(0, 1fr) auto 7.75rem;
                }
                #btnSaveNetWorth {
                  margin: 0;
                  padding: 0.25rem 0.65rem;
                  font-size: 0.85rem;
                }
                #btnSaveNetWorth:disabled {
                  opacity: 0.45;
                  cursor: not-allowed;
                  filter: none;
                }
                #saveNetWorthNotice {
                  margin: 0.5rem 0 0;
                }
                #saveNetWorthNotice.success {
                  color: #34d399;
                }
                .ledger-actions button:disabled {
                  opacity: 0.45;
                  cursor: not-allowed;
                  filter: none;
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
                    </div>
                    <section id="assetsSection" class="register-section">
                      <h2>Assets</h2>
                      <div id="assetsList"><p class="hint">Loading…</p></div>
                    </section>
                    <section id="liabilitiesSection" class="register-section">
                      <h2>Liabilities</h2>
                      <div id="liabilitiesList"><p class="hint">Loading…</p></div>
                    </section>
                    <section id="registerTotals" hidden></section>
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

    private static String escapeAttr(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
