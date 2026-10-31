/**
 * Snapshot assets and liabilities — unlock, bundle totals, and position CRUD.
 */
document.addEventListener('DOMContentLoaded', async () => {
  const assetsRoot = document.getElementById('assetsRoot');
  if (!assetsRoot) {
    return;
  }

  const SELECTED_BUNDLE_STORAGE_KEY = 'acruet.selectedBundleId';
  const DEFAULT_BUNDLE_NAME = 'Default';

  const state = {
    bundles: [],
    positions: [],
    positionCount: 0,
    positionLimit: 50,
    formMode: null,
    editingPositionId: null,
  };

  const els = {
    assetsLocked: document.getElementById('assetsLocked'),
    assetsBrowse: document.getElementById('assetsBrowse'),
    unlockTile: document.getElementById('unlockTile'),
    inlineUnlockForm: document.getElementById('inlineUnlockForm'),
    inlinePassphrase: document.getElementById('inlinePassphrase'),
    inlineUnlockError: document.getElementById('inlineUnlockError'),
    btnInlineUnlock: document.getElementById('btnInlineUnlock'),
    btnInlineUnlockCancel: document.getElementById('btnInlineUnlockCancel'),
    error: document.getElementById('assetsError'),
    registerMeta: document.getElementById('registerMeta'),
    assetsList: document.getElementById('assetsList'),
    liabilitiesList: document.getElementById('liabilitiesList'),
    registerTotals: document.getElementById('registerTotals'),
    formPanel: document.getElementById('formPanel'),
    formTitle: document.getElementById('formTitle'),
    formError: document.getElementById('formError'),
    formBody: document.getElementById('formBody'),
    btnAddAsset: document.querySelector('[data-action="create-asset"]'),
    btnAddLiability: document.querySelector('[data-action="create-liability"]'),
  };

  let formErrorTimeout = null;
  let renamingPositionId = null;

  window.AcruetAssets = {
    showInlineUnlock: () => showInlineUnlockForm(),
    onKeyLocked: () => syncLockState(),
  };

  await AcruetCrypto.session.ensureReady();
  syncLockState();
  document.addEventListener('acruet:crypto-changed', syncLockState);

  if (els.unlockTile) {
    els.unlockTile.addEventListener('click', showInlineUnlockForm);
  }
  if (els.btnInlineUnlock) {
    els.btnInlineUnlock.addEventListener('click', submitInlineUnlock);
  }
  if (els.inlinePassphrase) {
    els.inlinePassphrase.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        submitInlineUnlock();
      }
    });
  }
  if (els.btnInlineUnlockCancel) {
    els.btnInlineUnlockCancel.addEventListener('click', hideInlineUnlockForm);
  }

  els.assetsBrowse.addEventListener('click', (event) => {
    const button = event.target.closest('.ledger-action-btn');
    if (!button || button.disabled) {
      return;
    }
    handleAction(button.dataset.action);
  });

  document.getElementById('btnCancelForm').addEventListener('click', closeForm);
  document.getElementById('btnSubmitForm').addEventListener('click', submitForm);

  function syncLockState() {
    if (AcruetCrypto.session.isUnlocked()) {
      els.assetsLocked.hidden = true;
      hideInlineUnlockForm();
      els.assetsBrowse.hidden = false;
      refresh();
      return;
    }
    els.assetsLocked.hidden = false;
    els.assetsBrowse.hidden = true;
    closeForm(false);
    hideInlineUnlockForm();
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

  function handleAction(action) {
    if (action === 'envelopes') {
      window.location.href = '/';
      return;
    }
    if (action === 'create-asset') {
      showCreateForm('ASSET');
    } else if (action === 'create-liability') {
      showCreateForm('LIABILITY');
    }
  }

  async function refresh() {
    hideError();
    try {
      const [positionsBody, bundles] = await Promise.all([loadPositions(), loadBundles()]);
      state.positions = positionsBody.positions;
      state.positionCount = positionsBody.positionCount;
      state.positionLimit = positionsBody.positionLimit;
      state.bundles = bundles;
      render();
    } catch (error) {
      showError(error.message || 'Failed to load assets.');
    }
  }

  async function loadPositions() {
    const response = await fetch('/positions');
    if (!response.ok) {
      throw new Error('Failed to load snapshot items.');
    }
    const body = await response.json();
    const dek = AcruetCrypto.session.getDek();
    const decrypted = [];
    for (const position of body.positions || []) {
      const namePayload = await AcruetCrypto.decryptJson(dek, position.encryptedName);
      let cents = 0;
      let asOfDate = '';
      if (position.latestSnapshot) {
        const snapshot = await AcruetCrypto.decryptJson(dek, position.latestSnapshot.encryptedPayload);
        cents = Number(snapshot.cents ?? snapshot.totalCents ?? 0);
        asOfDate = position.latestSnapshot.asOfDate || '';
      }
      decrypted.push({
        ...position,
        name: namePayload.name,
        cents,
        asOfDate,
      });
    }
    decrypted.sort((left, right) =>
      String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
    return {
      positions: decrypted,
      positionCount: Number(body.positionCount || decrypted.length),
      positionLimit: Number(body.positionLimit || 50),
    };
  }

  async function loadBundles() {
    const response = await fetch('/ledger/bundles');
    if (!response.ok) {
      throw new Error('Failed to load envelope bundles.');
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
      bundles.push({
        ...bundle,
        name,
        totalCents: await bundleTotalCents(bundle.id, dek),
      });
    }
    return bundles.sort((left, right) =>
      String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
  }

  async function bundleTotalCents(bundleId, dek) {
    const query = `bundleId=${encodeURIComponent(bundleId)}`;
    const [accountsResponse, transactionsResponse] = await Promise.all([
      fetch(`/ledger/accounts?${query}`),
      fetch(`/ledger/transactions?${query}`),
    ]);
    if (!accountsResponse.ok || !transactionsResponse.ok) {
      throw new Error('Failed to load envelope totals.');
    }
    const accountsBody = await accountsResponse.json();
    const transactionsBody = await transactionsResponse.json();
    const activeIds = new Set();
    for (const account of accountsBody.accounts || []) {
      if (account.status === 'ACTIVE') {
        activeIds.add(account.id);
      }
    }
    let total = 0;
    for (const transaction of transactionsBody.transactions || []) {
      const payload = await AcruetCrypto.decryptJson(dek, transaction.encryptedPayload);
      (payload.lines || []).forEach((line) => {
        if (activeIds.has(line.accountId)) {
          total += line.amountCents;
        }
      });
    }
    return total;
  }

  function render() {
    const snapshotAssets = state.positions.filter((position) => position.kind === 'ASSET');
    const liabilities = state.positions.filter((position) => position.kind === 'LIABILITY');
    const envelopeTotal = state.bundles.reduce((sum, bundle) => sum + bundle.totalCents, 0);
    const snapshotAssetTotal = snapshotAssets.reduce((sum, position) => sum + position.cents, 0);
    const assetsTotal = envelopeTotal + snapshotAssetTotal;
    const liabilitiesTotal = liabilities.reduce((sum, position) => sum + position.cents, 0);
    const net = assetsTotal - liabilitiesTotal;

    els.registerMeta.hidden = false;
    els.registerMeta.textContent = `${state.positionCount} of ${state.positionLimit} snapshot items`;
    const atLimit = state.positionCount >= state.positionLimit;
    if (els.btnAddAsset) {
      els.btnAddAsset.disabled = atLimit;
      els.btnAddAsset.title = atLimit
        ? `Snapshot item limit reached (${state.positionLimit}).`
        : 'Add a snapshot asset';
    }
    if (els.btnAddLiability) {
      els.btnAddLiability.disabled = atLimit;
      els.btnAddLiability.title = atLimit
        ? `Snapshot item limit reached (${state.positionLimit}).`
        : 'Add a snapshot liability';
    }

    els.assetsList.innerHTML = renderAssetRows(snapshotAssets) || emptyHint('No snapshot assets yet.');
    els.liabilitiesList.innerHTML = renderPositionRows(liabilities)
      || emptyHint('No liabilities yet. Add a loan or other amount owed.');
    bindRowEvents(els.assetsList);
    bindRowEvents(els.liabilitiesList);
    bindBundleLinks();

    const netClass = net < 0 ? ' balance-negative' : '';
    els.registerTotals.hidden = false;
    els.registerTotals.innerHTML = `
      <div class="account-row total-row">
        <span class="name">Assets</span>
        <span class="balance">${formatCents(assetsTotal)}</span>
      </div>
      <div class="account-row">
        <span class="name">Liabilities</span>
        <span class="balance">${formatCents(liabilitiesTotal)}</span>
      </div>
      <div class="account-row total-row">
        <span class="name">Net</span>
        <span class="balance${netClass}">${formatCents(net)}</span>
      </div>`;
  }

  function renderAssetRows(snapshotAssets) {
    const bundleRows = state.bundles
      .map((bundle) => {
        const negativeClass = bundle.totalCents < 0 ? ' balance-negative' : '';
        return `
          <a class="account-row bundle-row" href="/" data-bundle-id="${bundle.id}">
            <span class="name">${escapeHtml(bundle.name)} · envelopes</span>
            <span class="balance${negativeClass}">${formatCents(bundle.totalCents)}</span>
          </a>`;
      })
      .join('');
    return bundleRows + renderPositionRows(snapshotAssets);
  }

  function renderPositionRows(positions) {
    return positions
      .map((position) => `
        <div class="account-row" data-position-id="${position.id}">
          <span class="name">
            <button type="button" class="envelope-name" data-id="${position.id}" title="Click to rename">${escapeHtml(position.name)}</button>
          </span>
          <span>
            ${position.asOfDate ? `<span class="as-of">${escapeHtml(position.asOfDate)}</span>` : ''}
            <button type="button" class="position-amount" data-id="${position.id}" title="Click to update value">${formatCents(position.cents)}</button>
            <button type="button" class="archive-btn secondary" data-id="${position.id}">Archive</button>
          </span>
        </div>`)
      .join('');
  }

  function emptyHint(text) {
    return `<p class="hint">${text}</p>`;
  }

  function bindRowEvents(list) {
    list.querySelectorAll('.envelope-name').forEach((button) => {
      button.addEventListener('click', () => startRename(button.dataset.id));
    });
    list.querySelectorAll('.position-amount').forEach((button) => {
      button.addEventListener('click', () => showUpdateForm(button.dataset.id));
    });
    list.querySelectorAll('.archive-btn').forEach((button) => {
      button.addEventListener('click', () => archivePosition(button.dataset.id));
    });
  }

  function bindBundleLinks() {
    els.assetsList.querySelectorAll('a.bundle-row').forEach((link) => {
      link.addEventListener('click', () => {
        window.localStorage.setItem(SELECTED_BUNDLE_STORAGE_KEY, link.dataset.bundleId);
      });
    });
  }

  function startRename(positionId) {
    const position = state.positions.find((item) => item.id === positionId);
    if (!position || renamingPositionId) {
      return;
    }
    renamingPositionId = positionId;
    const row = document.querySelector(`[data-position-id="${positionId}"]`);
    if (!row) {
      renamingPositionId = null;
      return;
    }
    const nameWrap = row.querySelector('.name');
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'envelope-name-input';
    input.value = position.name;
    input.maxLength = 120;
    input.setAttribute('aria-label', 'Item name');
    nameWrap.innerHTML = '';
    nameWrap.appendChild(input);
    input.focus();
    input.select();

    let finished = false;
    const finish = async (save) => {
      if (finished) {
        return;
      }
      finished = true;
      renamingPositionId = null;
      if (save) {
        const newName = input.value.trim();
        if (!newName) {
          showError('Name is required.');
          render();
          return;
        }
        if (newName !== position.name) {
          try {
            await renamePosition(positionId, newName);
          } catch (error) {
            showError(error.message || 'Failed to rename item.');
            render();
            return;
          }
        }
      }
      await refresh();
    };

    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        finish(true);
      } else if (event.key === 'Escape') {
        event.preventDefault();
        finish(false);
      }
    });
    input.addEventListener('blur', () => {
      finish(false);
    });
  }

  async function renamePosition(positionId, name) {
    const dek = AcruetCrypto.session.getDek();
    const encryptedName = await AcruetCrypto.encryptJson(dek, { name });
    const response = await fetch(`/positions/${positionId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ encryptedName }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Failed to rename item.');
    }
  }

  async function archivePosition(positionId) {
    const position = state.positions.find((item) => item.id === positionId);
    const label = position ? position.name : 'this item';
    if (!window.confirm(`Archive “${label}”? It leaves this register. History is kept for export.`)) {
      return;
    }
    const response = await fetch(`/positions/${positionId}/archive`, { method: 'POST' });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      showError(body.error || 'Failed to archive item.');
      return;
    }
    await refresh();
  }

  function openForm() {
    els.assetsBrowse.hidden = true;
    els.formPanel.hidden = false;
  }

  function closeForm(restoreBrowse = true) {
    hideFormError();
    els.formPanel.hidden = true;
    state.formMode = null;
    state.editingPositionId = null;
    els.formBody.innerHTML = '';
    if (restoreBrowse && AcruetCrypto.session.isUnlocked()) {
      els.assetsBrowse.hidden = false;
    }
  }

  function showCreateForm(kind) {
    if (state.positionCount >= state.positionLimit) {
      showError(`Snapshot item limit reached (${state.positionLimit}).`);
      return;
    }
    state.formMode = 'CREATE';
    state.editingPositionId = null;
    const liability = kind === 'LIABILITY';
    els.formTitle.textContent = liability ? 'Add liability' : 'Add asset';
    els.formBody.innerHTML = `
      <form id="positionForm">
        <input type="hidden" id="positionKind" value="${kind}">
        <label for="positionName">Name</label>
        <input id="positionName" type="text" required maxlength="120">
        <label for="positionAmount">${liability ? 'Amount owed' : 'Current value'}</label>
        <input id="positionAmount" type="text" inputmode="decimal" required placeholder="0.00">
        <label for="asOfDate">As of</label>
        <input id="asOfDate" type="date" required value="${todayIso()}">
        ${liability ? '<p class="hint">Enter the balance owed as a positive amount.</p>' : ''}
      </form>`;
    document.getElementById('positionForm').addEventListener('submit', (event) => {
      event.preventDefault();
      submitForm();
    });
    hideFormError();
    openForm();
    document.getElementById('positionName').focus();
  }

  function showUpdateForm(positionId) {
    const position = state.positions.find((item) => item.id === positionId);
    if (!position) {
      return;
    }
    state.formMode = 'UPDATE';
    state.editingPositionId = positionId;
    const liability = position.kind === 'LIABILITY';
    els.formTitle.textContent = `Update value — ${position.name}`;
    els.formBody.innerHTML = `
      <form id="positionForm">
        <label for="positionAmount">${liability ? 'Amount owed' : 'Current value'}</label>
        <input id="positionAmount" type="text" inputmode="decimal" required value="${formatUsdInput(position.cents)}">
        <label for="asOfDate">As of</label>
        <input id="asOfDate" type="date" required value="${todayIso()}">
        <p class="hint">This adds a new valuation. Earlier values stay in history.</p>
      </form>`;
    document.getElementById('positionForm').addEventListener('submit', (event) => {
      event.preventDefault();
      submitForm();
    });
    hideFormError();
    openForm();
    const amount = document.getElementById('positionAmount');
    amount.focus();
    amount.select();
  }

  async function submitForm() {
    hideError();
    hideFormError();
    try {
      if (state.formMode === 'CREATE') {
        await submitCreate();
      } else if (state.formMode === 'UPDATE') {
        await submitUpdate();
      }
      closeForm();
      await refresh();
    } catch (error) {
      showFormError(error.message || 'Could not save.');
    }
  }

  async function submitCreate() {
    const kind = document.getElementById('positionKind').value;
    const name = document.getElementById('positionName').value.trim();
    const cents = requireUsd(document.getElementById('positionAmount').value);
    const asOfDate = requireDate(document.getElementById('asOfDate').value);
    if (!name) {
      throw new Error('Name is required.');
    }
    const dek = AcruetCrypto.session.getDek();
    const encryptedName = await AcruetCrypto.encryptJson(dek, { name });
    const encryptedPayload = await AcruetCrypto.encryptJson(dek, { cents });
    const response = await fetch('/positions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ kind, encryptedName, asOfDate, encryptedPayload }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Failed to add item.');
    }
  }

  async function submitUpdate() {
    const cents = requireUsd(document.getElementById('positionAmount').value);
    const asOfDate = requireDate(document.getElementById('asOfDate').value);
    const dek = AcruetCrypto.session.getDek();
    const encryptedPayload = await AcruetCrypto.encryptJson(dek, { cents });
    const response = await fetch(`/positions/${state.editingPositionId}/snapshots`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ asOfDate, encryptedPayload }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Failed to update value.');
    }
  }

  function requireUsd(value) {
    if (value === null || value === undefined || String(value).trim() === '') {
      throw new Error('Amount is required.');
    }
    const dollars = Number.parseFloat(value);
    if (Number.isNaN(dollars) || dollars < 0) {
      throw new Error('Enter a positive dollar amount.');
    }
    return Math.round(dollars * 100);
  }

  function requireDate(value) {
    if (!value) {
      throw new Error('As-of date is required.');
    }
    return value;
  }

  function showFormError(message, durationMs = 5000) {
    if (!els.formError) {
      return;
    }
    if (formErrorTimeout) {
      clearTimeout(formErrorTimeout);
    }
    els.formError.textContent = message;
    els.formError.hidden = false;
    formErrorTimeout = setTimeout(() => {
      hideFormError();
    }, durationMs);
  }

  function hideFormError() {
    if (formErrorTimeout) {
      clearTimeout(formErrorTimeout);
      formErrorTimeout = null;
    }
    if (els.formError) {
      els.formError.hidden = true;
      els.formError.textContent = '';
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

  function formatCents(cents) {
    const sign = cents < 0 ? '-' : '';
    const abs = Math.abs(cents);
    return `${sign}$${(abs / 100).toFixed(2)}`;
  }

  function formatUsdInput(cents) {
    return (cents / 100).toFixed(2);
  }

  function todayIso() {
    return new Date().toISOString().slice(0, 10);
  }

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
});
