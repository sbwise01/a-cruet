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
  const ENVELOPES_GROUP_ID = '__envelopes__';
  const ENVELOPES_GROUP_NAME = 'Envelopes / Savings';

  const state = {
    bundles: [],
    positions: [],
    groups: [],
    positionCount: 0,
    positionLimit: 50,
    groupCount: 0,
    groupLimit: 20,
    formMode: null,
    editingPositionId: null,
    liveNet: null,
    expandedGroupIds: new Set(),
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
    assetsTotal: document.getElementById('assetsTotal'),
    liabilitiesTotal: document.getElementById('liabilitiesTotal'),
    netTotal: document.getElementById('netTotal'),
    registerTotals: document.getElementById('registerTotals'),
    saveNotice: document.getElementById('saveNetWorthNotice'),
    formPanel: document.getElementById('formPanel'),
    formTitle: document.getElementById('formTitle'),
    formError: document.getElementById('formError'),
    formBody: document.getElementById('formBody'),
    btnAddAsset: document.querySelector('[data-action="create-asset"]'),
    btnAddLiability: document.querySelector('[data-action="create-liability"]'),
    btnAddGroup: document.querySelector('[data-action="create-group"]'),
  };

  let formErrorTimeout = null;
  let renamingPositionId = null;
  let renamingGroupId = null;
  let groupPicker = null;

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

  window.AcruetAssets = {
    showInlineUnlock: () => deviceUnlockUi.showForm(),
    onKeyLocked: () => syncLockState(),
  };

  await AcruetCrypto.session.ensureReady();
  syncLockState();
  document.addEventListener('acruet:crypto-changed', syncLockState);

  els.assetsBrowse.addEventListener('click', (event) => {
    const button = event.target.closest('.ledger-action-btn');
    if (!button || button.disabled) {
      return;
    }
    handleAction(button.dataset.action);
  });

  document.getElementById('btnCancelForm').addEventListener('click', closeForm);
  document.getElementById('btnSubmitForm').addEventListener('click', submitForm);
  if (els.registerTotals) {
    els.registerTotals.addEventListener('click', (event) => {
      const button = event.target.closest('#btnSaveNetWorth');
      if (!button || button.disabled) {
        return;
      }
      void saveCurrentNetWorth();
    });
  }

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
    closeGroupPicker();
    state.liveNet = null;
    if (els.saveNotice) {
      els.saveNotice.hidden = true;
      els.saveNotice.textContent = '';
      els.saveNotice.classList.remove('success');
    }
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
    if (action === 'create-asset') {
      showCreateForm('ASSET');
    } else if (action === 'create-liability') {
      showCreateForm('LIABILITY');
    } else if (action === 'create-group') {
      showCreateGroupForm();
    }
  }

  async function refresh() {
    hideError();
    try {
      const [positionsBody, bundles, groupsBody] = await Promise.all([
        loadPositions(),
        loadBundles(),
        loadGroups(),
      ]);
      state.positions = positionsBody.positions;
      state.positionCount = positionsBody.positionCount;
      state.positionLimit = positionsBody.positionLimit;
      state.bundles = bundles;
      state.groups = groupsBody.groups;
      state.groupCount = groupsBody.groupCount;
      state.groupLimit = groupsBody.groupLimit;
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

  async function loadGroups() {
    const response = await fetch('/position-groups');
    if (!response.ok) {
      throw new Error('Failed to load groups.');
    }
    const body = await response.json();
    const dek = AcruetCrypto.session.getDek();
    const groups = [];
    for (const group of body.groups || []) {
      let name = 'Untitled';
      try {
        const payload = await AcruetCrypto.decryptJson(dek, group.encryptedName);
        name = payload.name || name;
      } catch (ignored) {
        name = 'Untitled';
      }
      groups.push({
        ...group,
        name,
      });
    }
    groups.sort((left, right) =>
      String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
    return {
      groups,
      groupCount: Number(body.groupCount || groups.length),
      groupLimit: Number(body.groupLimit || 20),
    };
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
    const snapshotAssets = sortByAmountDesc(
      state.positions.filter((position) => position.kind === 'ASSET'),
      (position) => position.cents,
    );
    const liabilities = sortByAmountDesc(
      state.positions.filter((position) => position.kind === 'LIABILITY'),
      (position) => position.cents,
    );
    const envelopeTotal = state.bundles.reduce((sum, bundle) => sum + bundle.totalCents, 0);
    const snapshotAssetTotal = snapshotAssets.reduce((sum, position) => sum + position.cents, 0);
    const assetsTotal = envelopeTotal + snapshotAssetTotal;
    const liabilitiesTotal = liabilities.reduce((sum, position) => sum + position.cents, 0);
    const net = assetsTotal - liabilitiesTotal;

    els.registerMeta.hidden = false;
    els.registerMeta.textContent =
      `${state.positionCount} of ${state.positionLimit} snapshot items · ${state.groupCount} of ${state.groupLimit} groups`;
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
    const atGroupLimit = state.groupCount >= state.groupLimit;
    if (els.btnAddGroup) {
      els.btnAddGroup.disabled = atGroupLimit;
      els.btnAddGroup.title = atGroupLimit
        ? `Group limit reached (${state.groupLimit}).`
        : 'Add a group';
    }

    closeGroupPicker();
    const hasUserGroups = state.groups.length > 0;
    els.assetsList.classList.toggle('has-expand', hasUserGroups || state.bundles.length > 0);
    els.assetsList.classList.toggle('has-groups', hasUserGroups);
    els.liabilitiesList.classList.toggle('has-expand', hasUserGroups);
    els.liabilitiesList.classList.toggle('has-groups', hasUserGroups);
    els.assetsList.innerHTML = renderAssetRows(snapshotAssets) || emptyHint('No snapshot assets yet.');
    els.liabilitiesList.innerHTML = renderGroupedRows(liabilities, { includeEmptyGroups: false })
      || emptyHint('No liabilities yet. Add a loan or other amount owed.');
    bindRowEvents(els.assetsList);
    bindRowEvents(els.liabilitiesList);
    bindBundleLinks();

    state.liveNet = {
      netCents: net,
      envelopeCents: envelopeTotal,
      assetCents: snapshotAssetTotal,
      liabilityCents: liabilitiesTotal,
    };
    if (els.assetsTotal) {
      els.assetsTotal.textContent = formatCents(assetsTotal);
    }
    if (els.liabilitiesTotal) {
      els.liabilitiesTotal.textContent = formatCents(liabilitiesTotal);
    }
    if (els.netTotal) {
      els.netTotal.textContent = formatCents(net);
      els.netTotal.classList.toggle('balance-negative', net < 0);
    }
    els.registerTotals.hidden = false;
  }

  async function saveCurrentNetWorth() {
    hideError();
    const button = document.getElementById('btnSaveNetWorth');
    if (!state.liveNet || !window.AcruetNetWorth) {
      showError('Net worth is still loading.');
      return;
    }
    if (button) {
      button.disabled = true;
      button.textContent = 'Saving…';
    }
    try {
      await AcruetNetWorth.create(
        AcruetNetWorth.localIsoDate(),
        AcruetNetWorth.computedPayload(state.liveNet),
      );
      showSaveNotice(`Saved net worth for ${AcruetNetWorth.localIsoDate()}.`);
    } catch (error) {
      showError(error.message || 'Failed to save net worth.');
    } finally {
      if (button) {
        button.disabled = false;
        button.textContent = 'Save net worth';
      }
    }
  }

  function showSaveNotice(message) {
    if (!els.saveNotice) {
      return;
    }
    els.saveNotice.textContent = message;
    els.saveNotice.classList.add('success');
    els.saveNotice.hidden = false;
  }

  function renderAssetRows(snapshotAssets) {
    return renderGroupedRows(snapshotAssets, { includeEmptyGroups: true, includeEnvelopes: true });
  }

  function renderGroupedRows(positions, { includeEmptyGroups, includeEnvelopes = false }) {
    const withExpand = state.groups.length > 0 || includeEnvelopes;
    const withGroupBtns = state.groups.length > 0;
    if (!withExpand) {
      return sortByAmountDesc(positions, (position) => position.cents)
        .map((position) => renderPositionRow(position, false, false, false))
        .join('');
    }
    const blocks = [];
    if (includeEnvelopes && state.bundles.length) {
      blocks.push({
        amount: state.bundles.reduce((sum, bundle) => sum + bundle.totalCents, 0),
        type: 'envelopes',
        items: sortByAmountDesc(state.bundles, (bundle) => bundle.totalCents),
      });
    }
    for (const group of state.groups) {
      const items = sortByAmountDesc(
        positions.filter((position) => position.groupId === group.id),
        (position) => position.cents,
      );
      const hasAnyKind = state.positions.some((position) => position.groupId === group.id);
      if (!items.length && (!includeEmptyGroups || hasAnyKind)) {
        continue;
      }
      const subtotal = items.reduce((sum, position) => sum + position.cents, 0);
      blocks.push({ amount: subtotal, type: 'group', group, items });
    }
    positions.filter((position) => !position.groupId).forEach((position) => {
      blocks.push({ amount: position.cents, type: 'item', position });
    });
    blocks.sort((left, right) => right.amount - left.amount);
    const parts = [];
    blocks.forEach((block) => {
      if (block.type === 'item') {
        parts.push(renderPositionRow(block.position, false, withExpand, withGroupBtns));
        return;
      }
      if (block.type === 'envelopes') {
        const expanded = state.expandedGroupIds.has(ENVELOPES_GROUP_ID);
        parts.push(renderEnvelopeGroupHeading(block.amount, expanded, withGroupBtns));
        if (expanded) {
          block.items.forEach((bundle) => parts.push(renderBundleRow(bundle, withExpand, withGroupBtns)));
        }
        return;
      }
      const expanded = state.expandedGroupIds.has(block.group.id);
      parts.push(renderGroupHeading(block.group, block.amount, expanded, withGroupBtns));
      if (expanded) {
        block.items.forEach((position) => parts.push(renderPositionRow(position, true, withExpand, withGroupBtns)));
      }
    });
    return parts.join('');
  }

  function renderBundleRow(bundle, withExpand, withGroupBtns) {
    const negativeClass = bundle.totalCents < 0 ? ' balance-negative' : '';
    return `
          <a class="account-row bundle-row grouped-item" href="/envelopes" data-bundle-id="${bundle.id}">
            ${expandSlotHtml(withExpand)}
            <span class="name">${escapeHtml(bundle.name)}</span>
            ${groupSlotHtml(withGroupBtns)}
            <span class="row-archive"></span>
            <span class="balance${negativeClass}">${formatCents(bundle.totalCents)}</span>
          </a>`;
  }

  function renderEnvelopeGroupHeading(cents, expanded, withGroupBtns) {
    const label = expanded ? `Collapse ${ENVELOPES_GROUP_NAME}` : `Expand ${ENVELOPES_GROUP_NAME}`;
    return `
        <div class="account-row group-heading" data-group-id="${ENVELOPES_GROUP_ID}">
          <span class="row-expand">
            <button type="button" class="group-expand" data-id="${ENVELOPES_GROUP_ID}" aria-expanded="${expanded ? 'true' : 'false'}" aria-label="${escapeHtml(label)}"></button>
          </span>
          <span class="name">${escapeHtml(ENVELOPES_GROUP_NAME)}</span>
          ${groupSlotHtml(withGroupBtns)}
          <span class="row-archive"></span>
          <span class="balance">${formatCents(cents)}</span>
        </div>`;
  }

  function renderGroupHeading(group, cents, expanded, withGroupBtns) {
    const label = expanded ? `Collapse ${group.name}` : `Expand ${group.name}`;
    return `
        <div class="account-row group-heading" data-group-id="${group.id}">
          <span class="row-expand">
            <button type="button" class="group-expand" data-id="${group.id}" aria-expanded="${expanded ? 'true' : 'false'}" aria-label="${escapeHtml(label)}"></button>
          </span>
          <span class="name">
            <button type="button" class="envelope-name group-name" data-id="${group.id}" title="Click to rename">${escapeHtml(group.name)}</button>
          </span>
          ${groupSlotHtml(withGroupBtns)}
          <span class="row-archive">
            <button type="button" class="archive-btn secondary archive-group-btn" data-id="${group.id}">Archive</button>
          </span>
          <span class="balance">${formatCents(cents)}</span>
        </div>`;
  }

  function renderPositionRow(position, nested, withExpand, withGroupBtns) {
    return `
        <div class="account-row${nested ? ' grouped-item' : ''}" data-position-id="${position.id}">
          ${expandSlotHtml(withExpand)}
          <span class="name">
            <button type="button" class="envelope-name" data-id="${position.id}" title="Click to rename">${escapeHtml(position.name)}</button>
          </span>
          ${groupButtonHtml(position, withGroupBtns)}
          <span class="row-archive">
            <button type="button" class="archive-btn secondary" data-id="${position.id}">Archive</button>
          </span>
          <button type="button" class="position-amount" data-id="${position.id}" title="Click to update value">${formatCents(position.cents)}</button>
        </div>`;
  }

  function expandSlotHtml(withExpand) {
    return withExpand ? '<span class="row-expand"></span>' : '';
  }

  function groupSlotHtml(withGroupBtns) {
    return withGroupBtns ? '<span class="row-group"></span>' : '';
  }

  function groupButtonHtml(position, withGroupBtns) {
    if (!withGroupBtns) {
      return '';
    }
    const group = state.groups.find((item) => item.id === position.groupId);
    const label = group ? group.name : 'Ungrouped';
    const assigned = group ? ' assigned' : '';
    return `<span class="row-group"><button type="button" class="group-btn${assigned}" data-id="${position.id}" title="Group: ${escapeHtml(label)}" aria-label="Set group, currently ${escapeHtml(label)}" aria-haspopup="listbox" aria-expanded="false">G</button></span>`;
  }

  function sortByAmountDesc(items, amountOf) {
    return items.slice().sort((left, right) => amountOf(right) - amountOf(left));
  }

  function emptyHint(text) {
    return `<p class="hint">${text}</p>`;
  }

  function bindRowEvents(list) {
    list.querySelectorAll('.envelope-name:not(.group-name)').forEach((button) => {
      button.addEventListener('click', () => startRename(button.dataset.id));
    });
    list.querySelectorAll('.group-name').forEach((button) => {
      button.addEventListener('click', () => startRenameGroup(button.dataset.id));
    });
    list.querySelectorAll('.group-expand').forEach((button) => {
      button.addEventListener('click', () => toggleGroupExpanded(button.dataset.id));
    });
    list.querySelectorAll('.position-amount').forEach((button) => {
      button.addEventListener('click', () => showUpdateForm(button.dataset.id));
    });
    list.querySelectorAll('.archive-group-btn').forEach((button) => {
      button.addEventListener('click', () => archiveGroup(button.dataset.id));
    });
    list.querySelectorAll('.archive-btn:not(.archive-group-btn)').forEach((button) => {
      button.addEventListener('click', () => archivePosition(button.dataset.id));
    });
    list.querySelectorAll('.group-btn').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        toggleGroupPicker(button);
      });
    });
  }

  function toggleGroupExpanded(groupId) {
    if (state.expandedGroupIds.has(groupId)) {
      state.expandedGroupIds.delete(groupId);
    } else {
      state.expandedGroupIds.add(groupId);
    }
    render();
  }

  function bindBundleLinks() {
    els.assetsList.querySelectorAll('a.bundle-row').forEach((link) => {
      link.addEventListener('click', () => {
        window.localStorage.setItem(SELECTED_BUNDLE_STORAGE_KEY, link.dataset.bundleId);
      });
    });
  }

  function toggleGroupPicker(button) {
    const positionId = button.dataset.id;
    if (groupPicker && groupPicker.dataset.positionId === positionId) {
      closeGroupPicker();
      return;
    }
    closeGroupPicker();
    const position = state.positions.find((item) => item.id === positionId);
    if (!position) {
      return;
    }
    button.setAttribute('aria-expanded', 'true');
    groupPicker = document.createElement('div');
    groupPicker.id = 'groupPicker';
    groupPicker.className = 'group-picker';
    groupPicker.dataset.positionId = positionId;
    groupPicker.setAttribute('role', 'listbox');
    groupPicker.innerHTML = [
      pickerOptionHtml('', 'Ungrouped', !position.groupId),
      ...state.groups.map((group) => pickerOptionHtml(group.id, group.name, position.groupId === group.id)),
    ].join('');
    document.body.appendChild(groupPicker);
    placeGroupPicker(button);
    groupPicker.querySelectorAll('button').forEach((option) => {
      option.addEventListener('click', () => {
        const nextId = option.dataset.groupId || null;
        closeGroupPicker();
        if ((position.groupId || null) === nextId) {
          return;
        }
        void assignGroup(positionId, nextId);
      });
    });
  }

  function pickerOptionHtml(groupId, label, selected) {
    return `<button type="button" role="option" data-group-id="${groupId}" aria-selected="${selected ? 'true' : 'false'}">${escapeHtml(label)}</button>`;
  }

  function placeGroupPicker(button) {
    const rect = button.getBoundingClientRect();
    const menuWidth = groupPicker.offsetWidth;
    const menuHeight = groupPicker.offsetHeight;
    let left = rect.left;
    let top = rect.bottom + 4;
    if (left + menuWidth > window.innerWidth - 8) {
      left = Math.max(8, rect.right - menuWidth);
    }
    if (top + menuHeight > window.innerHeight - 8) {
      top = Math.max(8, rect.top - menuHeight - 4);
    }
    groupPicker.style.left = `${left}px`;
    groupPicker.style.top = `${top}px`;
  }

  function closeGroupPicker() {
    if (groupPicker) {
      groupPicker.remove();
      groupPicker = null;
    }
    document.querySelectorAll('.group-btn[aria-expanded="true"]').forEach((button) => {
      button.setAttribute('aria-expanded', 'false');
    });
  }

  document.addEventListener('mousedown', (event) => {
    if (!groupPicker) {
      return;
    }
    if (event.target.closest('.group-picker') || event.target.closest('.group-btn')) {
      return;
    }
    closeGroupPicker();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && groupPicker) {
      event.preventDefault();
      closeGroupPicker();
    }
  });
  window.addEventListener('resize', closeGroupPicker);

  async function assignGroup(positionId, groupId) {
    hideError();
    const response = await fetch(`/positions/${positionId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ groupId }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      showError(body.error || 'Failed to assign group.');
      await refresh();
      return;
    }
    await refresh();
  }

  function startRenameGroup(groupId) {
    const group = state.groups.find((item) => item.id === groupId);
    if (!group || renamingGroupId || renamingPositionId) {
      return;
    }
    renamingGroupId = groupId;
    const row = document.querySelector(`[data-group-id="${groupId}"]`);
    if (!row) {
      renamingGroupId = null;
      return;
    }
    const nameWrap = row.querySelector('.name');
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'envelope-name-input';
    input.value = group.name;
    input.maxLength = 120;
    input.setAttribute('aria-label', 'Group name');
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
      renamingGroupId = null;
      if (save) {
        let newName;
        try {
          newName = requireGroupName(input.value);
        } catch (error) {
          showError(error.message);
          render();
          return;
        }
        if (newName !== group.name) {
          try {
            await renameGroup(groupId, newName);
          } catch (error) {
            showError(error.message || 'Failed to rename group.');
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

  async function renameGroup(groupId, name) {
    const dek = AcruetCrypto.session.getDek();
    const encryptedName = await AcruetCrypto.encryptJson(dek, { name });
    const response = await fetch(`/position-groups/${groupId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ encryptedName }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Failed to rename group.');
    }
  }

  async function archiveGroup(groupId) {
    const group = state.groups.find((item) => item.id === groupId);
    const label = group ? group.name : 'this group';
    const stillUsed = state.positions.some((position) => position.groupId === groupId);
    if (stillUsed) {
      showError('Move or ungroup active items before archiving this group.');
      return;
    }
    if (!window.confirm(`Archive “${label}”?`)) {
      return;
    }
    const response = await fetch(`/position-groups/${groupId}/archive`, { method: 'POST' });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      showError(body.error || 'Failed to archive group.');
      return;
    }
    await refresh();
  }

  function startRename(positionId) {
    const position = state.positions.find((item) => item.id === positionId);
    if (!position || renamingPositionId || renamingGroupId) {
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
        <label for="positionGroup">Group</label>
        <select id="positionGroup">${groupOptionsHtml('')}</select>
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

  function showCreateGroupForm() {
    if (state.groupCount >= state.groupLimit) {
      showError(`Group limit reached (${state.groupLimit}).`);
      return;
    }
    state.formMode = 'CREATE_GROUP';
    state.editingPositionId = null;
    els.formTitle.textContent = 'Add group';
    els.formBody.innerHTML = `
      <form id="positionForm">
        <label for="groupName">Name</label>
        <input id="groupName" type="text" required maxlength="120" placeholder="Investments, Autos, …">
        <p class="hint">A label for snapshot items. Membership is optional.</p>
      </form>`;
    document.getElementById('positionForm').addEventListener('submit', (event) => {
      event.preventDefault();
      submitForm();
    });
    hideFormError();
    openForm();
    document.getElementById('groupName').focus();
  }

  function groupOptionsHtml(selectedId) {
    const options = [`<option value=""${selectedId ? '' : ' selected'}>Ungrouped</option>`];
    state.groups.forEach((group) => {
      options.push(
        `<option value="${group.id}"${selectedId === group.id ? ' selected' : ''}>${escapeHtml(group.name)}</option>`,
      );
    });
    return options.join('');
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
      } else if (state.formMode === 'CREATE_GROUP') {
        await submitCreateGroup();
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
    const groupId = document.getElementById('positionGroup').value || null;
    const dek = AcruetCrypto.session.getDek();
    const encryptedName = await AcruetCrypto.encryptJson(dek, { name });
    const encryptedPayload = await AcruetCrypto.encryptJson(dek, { cents });
    const payload = { kind, encryptedName, asOfDate, encryptedPayload };
    if (groupId) {
      payload.groupId = groupId;
    }
    const response = await fetch('/positions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Failed to add item.');
    }
  }

  async function submitCreateGroup() {
    const name = requireGroupName(document.getElementById('groupName').value);
    const dek = AcruetCrypto.session.getDek();
    const encryptedName = await AcruetCrypto.encryptJson(dek, { name });
    const response = await fetch('/position-groups', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ encryptedName }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Failed to add group.');
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

  function requireGroupName(name) {
    const trimmed = String(name || '').trim();
    if (!trimmed) {
      throw new Error('Name is required.');
    }
    if (isReservedGroupName(trimmed)) {
      throw new Error(`${ENVELOPES_GROUP_NAME} is reserved for envelope cash.`);
    }
    return trimmed;
  }

  function isReservedGroupName(name) {
    return normalizeGroupName(name) === normalizeGroupName(ENVELOPES_GROUP_NAME);
  }

  function normalizeGroupName(name) {
    return String(name || '').trim().replace(/\s+/g, ' ').toLowerCase();
  }

  function formatCents(cents) {
    const value = Number(cents || 0) / 100;
    const sign = value < 0 ? '-' : '';
    return `${sign}$${Math.abs(value).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
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
