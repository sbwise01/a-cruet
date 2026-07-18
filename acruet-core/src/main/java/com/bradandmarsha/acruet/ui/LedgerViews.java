package com.bradandmarsha.acruet.ui;

/**
 * Shared ledger dashboard HTML and styles (Phase 9 items 5–9).
 */
public final class LedgerViews {

    private LedgerViews() {
    }

    public static String ledgerCss() {
        return PageStyles.formCss() + """
                button.secondary { background: #475569; color: var(--text); }
                button.archive-btn { margin-left: 0.5rem; padding: 0.25rem 0.5rem; font-size: 0.85rem; margin-top: 0; }
                .notice { margin-top: 1rem; padding: 0.75rem 1rem; border-radius: 8px; background: var(--bg-card); border-left: 3px solid #fbbf24; }
                .balance-negative { color: #fbbf24; }
                .account-row {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  padding: 0.65rem 0;
                  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                }
                .account-row .name { font-weight: 600; }
                .account-row .balance { font-variant-numeric: tabular-nums; }
                .account-row.total-row {
                  margin-top: 0.35rem;
                  padding-top: 0.85rem;
                  border-top: 2px solid rgba(148, 163, 184, 0.35);
                  border-bottom: none;
                }
                .form-grid label { margin-top: 0.75rem; }
                .line-row { display: flex; gap: 0.5rem; margin-top: 0.5rem; align-items: center; }
                .line-row select, .line-row input { flex: 1; margin-top: 0; }
                #formPanel { margin-top: 0; }
                .form-error {
                  margin: 0 0 1rem;
                  padding: 0.75rem 1rem;
                  border-radius: 8px;
                  background: rgba(248, 113, 113, 0.12);
                  border-left: 3px solid #f87171;
                  color: #f87171;
                }
                h2 .heading-meta { color: var(--muted); font-weight: 600; font-size: 1rem; }
                .ledger-actions {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 0.5rem;
                  margin: 0.75rem 0 1rem;
                }
                .ledger-actions button { margin-top: 0; }
                .ledger-actions-bottom { margin-top: 1.25rem; }
                .unlock-tile-wrap { display: flex; justify-content: center; padding: 2rem 0 1rem; }
                .unlock-tile {
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  gap: 0.75rem;
                  width: 160px;
                  background: var(--bg-card);
                  border: 1px solid rgba(148, 163, 184, 0.12);
                  border-radius: 16px;
                  padding: 1.5rem 1rem;
                  color: var(--text);
                  cursor: pointer;
                  font: inherit;
                  transition: transform 0.15s ease, background 0.15s ease, border-color 0.15s ease;
                }
                .unlock-tile:hover {
                  transform: translateY(-4px);
                  background: #334155;
                  border-color: var(--accent);
                }
                .unlock-tile img {
                  width: 64px;
                  height: 64px;
                  object-fit: contain;
                  border-radius: 12px;
                }
                .unlock-tile-label { font-weight: 600; }
                #inlineUnlockForm { max-width: 24rem; margin: 1.5rem auto 0; }
                """;
    }

    public static String ledgerMainHtml(String lockImageUrl) {
        return """
                <div id="ledgerRoot" data-lock-image="%s">
                  <div id="ledgerLocked" hidden>
                    <div class="unlock-tile-wrap">
                      <button type="button" id="unlockTile" class="unlock-tile">
                        <img src="%s" alt="Locked">
                        <span class="unlock-tile-label">Unlock</span>
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
                    </div>
                  </div>
                  <div id="ledgerBrowse">
                    <div id="ledgerError" class="error" hidden></div>
                    <div id="ledgerWarning" class="notice" hidden></div>
                    <section id="accountsPanel">
                      <h2 id="envelopesHeading">Envelopes <span class="heading-meta">(…)</span></h2>
                      %s
                      <div id="accountsList"><p class="hint">Loading…</p></div>
                      %s
                    </section>
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
                <script src="/static/js/acruet-ledger.js"></script>
                """
                .formatted(
                        escapeAttr(lockImageUrl),
                        escapeAttr(lockImageUrl),
                        actionButtonsHtml(false),
                        actionButtonsHtml(true));
    }

    private static String actionButtonsHtml(boolean bottom) {
        String bottomClass = bottom ? " ledger-actions-bottom" : "";
        return """
                <div class="ledger-actions%s">
                  <button type="button" class="ledger-action-btn" data-action="create">New envelope</button>
                  <button type="button" class="ledger-action-btn" data-action="deposit">Deposit</button>
                  <button type="button" class="ledger-action-btn" data-action="withdraw">Withdraw</button>
                  <button type="button" class="ledger-action-btn" data-action="transfer">Transfer</button>
                </div>
                """
                .formatted(bottomClass);
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
