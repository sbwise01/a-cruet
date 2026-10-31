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
                .account-row .as-of {
                  color: var(--muted);
                  font-size: 0.85rem;
                  margin-right: 0.65rem;
                  font-variant-numeric: tabular-nums;
                }
                .position-amount {
                  font: inherit;
                  font-variant-numeric: tabular-nums;
                  color: inherit;
                  background: transparent;
                  border: none;
                  padding: 0.1rem 0.35rem;
                  margin: 0;
                  border-radius: 4px;
                  cursor: pointer;
                }
                .position-amount:hover { background: rgba(148, 163, 184, 0.15); }
                .register-section { margin-bottom: 2rem; }
                .register-meta { margin: 0 0 0.75rem; }
                #registerTotals { margin-top: 0.5rem; }
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
                    <div id="inlineUnlockForm" hidden>
                      <h2>Unlock your ledger</h2>
                      <p class="hint">Enter your passphrase to decrypt data for this session.</p>
                      <label for="inlinePassphrase">Passphrase</label>
                      <input id="inlinePassphrase" type="password" autocomplete="current-password">
                      <p id="inlineUnlockError" class="error" hidden></p>
                      <button type="button" id="btnInlineUnlock">Unlock</button>
                      <button type="button" id="btnInlineUnlockCancel" class="secondary">Cancel</button>
                      <p class="actions"><a href="/keys/forgot-passphrase">Forgot passphrase?</a></p>
                    </div>
                  </div>
                  <div id="assetsBrowse">
                    <div id="assetsError" class="error" hidden></div>
                    <p id="registerMeta" class="hint register-meta" hidden></p>
                    <div class="ledger-actions">
                      <button type="button" class="ledger-action-btn" data-action="envelopes">Envelopes</button>
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
                """.formatted(escapeAttr(lockImageUrl), escapeAttr(lockImageUrl))
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
