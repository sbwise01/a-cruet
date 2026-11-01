/**
 * Standalone reports page — unlock, bundle picker, and report data.
 */
document.addEventListener('DOMContentLoaded', async () => {
  const reportsRoot = document.getElementById('reportsRoot');
  if (!reportsRoot) {
    return;
  }

  const SELECTED_BUNDLE_STORAGE_KEY = 'acruet.selectedBundleId';
  const REPORTS_BUNDLE_STORAGE_KEY = 'acruet.reports.selectedBundleId';
  const ALL_BUNDLES = '__all__';
  const DEFAULT_BUNDLE_NAME = 'Default';
  const reportKind = reportsRoot.dataset.report || 'hub';

  const state = {
    bundles: [],
    selectedBundleId: null,
    accounts: [],
  };

  const els = {
    reportsLocked: document.getElementById('reportsLocked'),
    reportsBrowse: document.getElementById('reportsBrowse'),
    unlockTile: document.getElementById('unlockTile'),
    inlineUnlockForm: document.getElementById('inlineUnlockForm'),
    inlinePassphrase: document.getElementById('inlinePassphrase'),
    inlineUnlockError: document.getElementById('inlineUnlockError'),
    btnInlineUnlock: document.getElementById('btnInlineUnlock'),
    btnInlineUnlockCancel: document.getElementById('btnInlineUnlockCancel'),
    error: document.getElementById('reportsPageError'),
    bundleSelect: document.getElementById('bundleSelect'),
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

  window.AcruetReportsPage = {
    showInlineUnlock: () => deviceUnlockUi.showForm(),
    onKeyLocked: () => syncLockState(),
  };

  await AcruetCrypto.session.ensureReady();
  syncLockState();
  document.addEventListener('acruet:crypto-changed', syncLockState);
  if (els.bundleSelect) {
    els.bundleSelect.addEventListener('change', async () => {
      state.selectedBundleId = els.bundleSelect.value || null;
      persistSelectedBundle();
      await refreshAccounts();
    });
  }

  function syncLockState() {
    if (AcruetCrypto.session.isUnlocked()) {
      els.reportsLocked.hidden = true;
      hideInlineUnlockForm();
      els.reportsBrowse.hidden = false;
      refresh();
      return;
    }
    els.reportsLocked.hidden = false;
    els.reportsBrowse.hidden = true;
    hideInlineUnlockForm();
    if (window.AcruetReports) {
      window.AcruetReports.closeAll();
    }
  }

  function showInlineUnlockForm() {
    if (!els.inlineUnlockForm) {
      return;
    }
    els.unlockTile.closest('.unlock-tile-wrap').hidden = true;
    els.inlineUnlockForm.hidden = false;
    els.inlineUnlockError.hidden = true;
    els.inlinePassphrase.value = '';
    els.inlinePassphrase.focus();
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

  async function submitInlineUnlock() {
    els.inlineUnlockError.hidden = true;
    els.btnInlineUnlock.disabled = true;
    els.btnInlineUnlock.textContent = 'Unlocking…';
    const passphrase = els.inlinePassphrase.value;
    if (!passphrase) {
      showInlineError('Enter your passphrase.');
      els.btnInlineUnlock.disabled = false;
      els.btnInlineUnlock.textContent = 'Unlock';
      return;
    }
    try {
      const response = await fetch('/keys/wrapped-dek');
      if (!response.ok) {
        throw new Error('Could not load wrapped encryption key.');
      }
      const payload = await response.json();
      await AcruetCrypto.session.unlock(passphrase, payload);
      hideInlineUnlockForm();
      syncLockState();
    } catch (error) {
      showInlineError('Incorrect passphrase or unlock failed.');
      console.error(error);
    } finally {
      els.btnInlineUnlock.disabled = false;
      els.btnInlineUnlock.textContent = 'Unlock';
    }
  }

  function showInlineError(message) {
    els.inlineUnlockError.textContent = message;
    els.inlineUnlockError.hidden = false;
  }

  async function refresh() {
    hideError();
    try {
      if (reportKind === 'hub') {
        return;
      }
      if (reportKind === 'net-worth') {
        initReports();
        return;
      }
      await loadBundles();
      if (!state.selectedBundleId) {
        state.accounts = [];
        initReports();
        return;
      }
      await refreshAccounts();
    } catch (error) {
      showError(error.message || 'Failed to load reports.');
    }
  }

  async function loadBundles() {
    const response = await fetch('/ledger/bundles');
    if (!response.ok) {
      throw new Error('Failed to load bundles.');
    }
    const body = await response.json();
    const dek = AcruetCrypto.session.getDek();
    const bundles = [];
    for (const bundle of body.bundles || []) {
      let name = '';
      if (bundle.encryptedName) {
        const payload = await AcruetCrypto.decryptJson(dek, bundle.encryptedName);
        name = payload.name;
      }
      if (!name) {
        name = bundle.defaultBundle ? DEFAULT_BUNDLE_NAME : 'Untitled';
      }
      bundles.push({ ...bundle, name });
    }
    state.bundles = bundles.sort((left, right) =>
      String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
    pickSelectedBundle();
    renderBundleSwitcher();
  }

  function pickSelectedBundle() {
    const reportsPreferred = state.selectedBundleId || window.localStorage.getItem(REPORTS_BUNDLE_STORAGE_KEY);
    if (reportsPreferred === ALL_BUNDLES) {
      state.selectedBundleId = ALL_BUNDLES;
      persistSelectedBundle();
      return;
    }
    const preferred = reportsPreferred || window.localStorage.getItem(SELECTED_BUNDLE_STORAGE_KEY);
    if (preferred && state.bundles.some((bundle) => bundle.id === preferred)) {
      state.selectedBundleId = preferred;
      persistSelectedBundle();
      return;
    }
    const fallback = state.bundles.find((bundle) => bundle.defaultBundle) || state.bundles[0];
    state.selectedBundleId = fallback ? fallback.id : null;
    persistSelectedBundle();
  }

  function persistSelectedBundle() {
    if (state.selectedBundleId === ALL_BUNDLES) {
      window.localStorage.setItem(REPORTS_BUNDLE_STORAGE_KEY, ALL_BUNDLES);
      return;
    }
    if (!state.selectedBundleId) {
      window.localStorage.removeItem(REPORTS_BUNDLE_STORAGE_KEY);
      window.localStorage.removeItem(SELECTED_BUNDLE_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(REPORTS_BUNDLE_STORAGE_KEY, state.selectedBundleId);
    window.localStorage.setItem(SELECTED_BUNDLE_STORAGE_KEY, state.selectedBundleId);
  }

  function renderBundleSwitcher() {
    if (!els.bundleSelect) {
      return;
    }
    const allSelected = state.selectedBundleId === ALL_BUNDLES ? ' selected' : '';
    const bundleOptions = state.bundles
      .map((bundle) => {
        const selected = bundle.id === state.selectedBundleId ? ' selected' : '';
        return `<option value="${bundle.id}"${selected}>${escapeHtml(bundle.name)}</option>`;
      })
      .join('');
    els.bundleSelect.innerHTML = `<option value="${ALL_BUNDLES}"${allSelected}>All bundles</option>${bundleOptions}`;
  }

  function isAllBundles() {
    return state.selectedBundleId === ALL_BUNDLES;
  }

  async function refreshAccounts() {
    if (isAllBundles()) {
      state.accounts = await loadAccountsForBundles(state.bundles);
      initReports();
      return;
    }
    if (!state.selectedBundleId) {
      state.accounts = [];
      initReports();
      return;
    }
    const bundle = state.bundles.find((item) => item.id === state.selectedBundleId);
    state.accounts = await loadAccountsForBundles(bundle ? [bundle] : []);
    initReports();
  }

  async function loadAccountsForBundles(bundles) {
    const prefixNames = bundles.length > 1;
    const accounts = [];
    for (const bundle of bundles) {
      const response = await fetch(`/ledger/accounts?bundleId=${encodeURIComponent(bundle.id)}`);
      if (!response.ok) {
        throw new Error('Failed to load envelopes.');
      }
      const body = await response.json();
      const decrypted = await decryptAccounts(body.accounts || []);
      decrypted.forEach((account) => {
        accounts.push({
          ...account,
          bundleId: bundle.id,
          bundleName: bundle.name,
          name: prefixNames ? `${bundle.name} · ${account.name}` : account.name,
        });
      });
    }
    return accounts.sort((left, right) =>
      String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
  }

  async function decryptAccounts(accounts) {
    const dek = AcruetCrypto.session.getDek();
    const decrypted = [];
    for (const account of accounts) {
      const payload = await AcruetCrypto.decryptJson(dek, account.encryptedName);
      decrypted.push({ ...account, name: payload.name });
    }
    return decrypted
      .filter((account) => account.status === 'ACTIVE')
      .sort((left, right) =>
        String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
  }

  async function decryptTransactions(transactions) {
    const dek = AcruetCrypto.session.getDek();
    const decrypted = [];
    for (const transaction of transactions) {
      const payload = await AcruetCrypto.decryptJson(dek, transaction.encryptedPayload);
      decrypted.push({ ...transaction, payload });
    }
    return decrypted;
  }

  function initReports() {
    if (!window.AcruetReports) {
      return;
    }
    window.AcruetReports.init({
      getAccounts: () => state.accounts,
      getBundleId: () => (isAllBundles() ? null : state.selectedBundleId),
      getBundleIds: () => state.bundles.map((bundle) => bundle.id),
      isAllBundles,
      getBundleName: () => {
        if (isAllBundles()) {
          return '';
        }
        const bundle = state.bundles.find((item) => item.id === state.selectedBundleId);
        return bundle ? bundle.name : '';
      },
      decryptTransactions,
      escapeHtml,
      requireUnlocked: () => {
        if (!AcruetCrypto.session.isUnlocked()) {
          throw new Error('Unlock your ledger before running reports.');
        }
      },
      beforeReportsOpen: () => {},
      afterReportsClose: () => {},
    });
    window.AcruetReports.showReportsEntry();
  }

  function showError(message) {
    els.error.textContent = message;
    els.error.hidden = false;
  }

  function hideError() {
    els.error.hidden = true;
    els.error.textContent = '';
  }

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
});
