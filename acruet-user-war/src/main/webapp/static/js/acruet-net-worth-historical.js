/**
 * Add a MANUAL net-worth observation (year-end backfill).
 */
document.addEventListener('DOMContentLoaded', async () => {
  const root = document.getElementById('historicalNetWorthRoot');
  if (!root) {
    return;
  }

  const els = {
    locked: document.getElementById('historicalLocked'),
    browse: document.getElementById('historicalBrowse'),
    unlockTile: document.getElementById('unlockTile'),
    inlineUnlockForm: document.getElementById('inlineUnlockForm'),
    inlinePassphrase: document.getElementById('inlinePassphrase'),
    inlineUnlockError: document.getElementById('inlineUnlockError'),
    btnInlineUnlock: document.getElementById('btnInlineUnlock'),
    btnInlineUnlockCancel: document.getElementById('btnInlineUnlockCancel'),
    error: document.getElementById('historicalError'),
    notice: document.getElementById('historicalNotice'),
    asOf: document.getElementById('netWorthAsOf'),
    amount: document.getElementById('netWorthAmount'),
    submit: document.getElementById('btnAddHistoricalNetWorth'),
  };

  const deviceUnlockUi = AcruetDeviceUnlock.attachInline({
    form: els.inlineUnlockForm,
    tile: els.unlockTile,
    passphraseInput: els.inlinePassphrase,
    errorEl: els.inlineUnlockError,
    unlockButton: els.btnInlineUnlock,
    cancelButton: els.btnInlineUnlockCancel,
    onUnlocked: () => {
      hideInlineUnlockForm();
      syncLockState();
    },
    onCancel: hideInlineUnlockForm,
  });

  window.AcruetHistoricalNetWorth = {
    showInlineUnlock: () => deviceUnlockUi.showForm(),
    onKeyLocked: () => syncLockState(),
  };

  await AcruetCrypto.session.ensureReady();
  syncLockState();
  document.addEventListener('acruet:crypto-changed', syncLockState);
  if (els.submit) {
    els.submit.addEventListener('click', () => void submitHistorical());
  }

  function syncLockState() {
    if (AcruetCrypto.session.isUnlocked()) {
      els.locked.hidden = true;
      hideInlineUnlockForm();
      els.browse.hidden = false;
      return;
    }
    els.locked.hidden = false;
    els.browse.hidden = true;
    hideInlineUnlockForm();
  }

  function hideInlineUnlockForm() {
    if (!els.inlineUnlockForm) {
      return;
    }
    els.inlineUnlockForm.hidden = true;
    if (els.unlockTile) {
      els.unlockTile.closest('.unlock-tile-wrap').hidden = false;
    }
    els.inlineUnlockError.hidden = true;
  }

  async function submitHistorical() {
    hideError();
    hideNotice();
    if (!window.AcruetNetWorth) {
      showError('Net worth helper failed to load.');
      return;
    }
    els.submit.disabled = true;
    els.submit.textContent = 'Saving…';
    try {
      const asOfDate = els.asOf.value;
      if (!asOfDate) {
        throw new Error('As-of date is required.');
      }
      const netCents = AcruetNetWorth.parseUsd(els.amount.value);
      await AcruetNetWorth.create(asOfDate, AcruetNetWorth.manualPayload(netCents));
      els.amount.value = '';
      showNotice(`Saved historical net worth for ${asOfDate}.`);
    } catch (error) {
      showError(error.message || 'Failed to add historical total.');
    } finally {
      els.submit.disabled = false;
      els.submit.textContent = 'Add historical total';
    }
  }

  function showError(message) {
    els.error.textContent = message;
    els.error.hidden = false;
  }

  function hideError() {
    els.error.hidden = true;
    els.error.textContent = '';
  }

  function showNotice(message) {
    els.notice.textContent = message;
    els.notice.classList.add('success');
    els.notice.hidden = false;
  }

  function hideNotice() {
    els.notice.hidden = true;
    els.notice.textContent = '';
    els.notice.classList.remove('success');
  }
});
