/**
 * Household net-worth register — encrypt, list, and save observations.
 */
window.AcruetNetWorth = (() => {
  const LIMIT = 600;
  const SOURCE_COMPUTED = 'COMPUTED';
  const SOURCE_MANUAL = 'MANUAL';

  function localIsoDate(date = new Date()) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function formatCents(cents) {
    const value = Number(cents || 0) / 100;
    const sign = value < 0 ? '-' : '';
    return `${sign}$${Math.abs(value).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  }

  function parseUsd(value, { allowNegative = true } = {}) {
    if (value === null || value === undefined || String(value).trim() === '') {
      throw new Error('Amount is required.');
    }
    const normalized = String(value).replace(/[$,\s]/g, '');
    const dollars = Number.parseFloat(normalized);
    if (Number.isNaN(dollars) || (!allowNegative && dollars < 0)) {
      throw new Error(allowNegative ? 'Enter a dollar amount.' : 'Enter a positive dollar amount.');
    }
    return Math.round(dollars * 100);
  }

  function sourceLabel(source) {
    if (source === SOURCE_COMPUTED) {
      return 'Computed';
    }
    if (source === SOURCE_MANUAL) {
      return 'Manual';
    }
    return source || 'Unknown';
  }

  function computedPayload({ netCents, envelopeCents, assetCents, liabilityCents }) {
    return {
      netCents,
      source: SOURCE_COMPUTED,
      envelopeCents,
      assetCents,
      liabilityCents,
    };
  }

  function manualPayload(netCents) {
    return {
      netCents,
      source: SOURCE_MANUAL,
    };
  }

  function requireUnlocked() {
    if (typeof AcruetCrypto === 'undefined' || !AcruetCrypto.session.isUnlocked()) {
      throw new Error('Unlock your ledger first.');
    }
  }

  function numberOrNull(value) {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    const number = Number(value);
    return Number.isNaN(number) ? null : number;
  }

  async function latest() {
    const response = await fetch('/net-worth/latest');
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || 'Failed to load latest net worth.');
    }
    return {
      snapshot: body.snapshot || null,
      count: Number(body.count || 0),
      limit: Number(body.limit || LIMIT),
    };
  }

  function addCalendarMonth(isoDate) {
    const [year, month, day] = String(isoDate).split('-').map(Number);
    const next = new Date(year, month - 1, 1);
    next.setMonth(next.getMonth() + 1);
    const lastDay = new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate();
    next.setDate(Math.min(day, lastDay));
    return localIsoDate(next);
  }

  function isStale(asOfDate, todayIso = localIsoDate()) {
    if (!asOfDate) {
      return true;
    }
    return addCalendarMonth(asOfDate) <= todayIso;
  }

  function snoozeUntil(todayIso = localIsoDate()) {
    const [year, month, day] = todayIso.split('-').map(Number);
    const until = new Date(year, month - 1, day);
    until.setDate(until.getDate() + 7);
    return localIsoDate(until);
  }

  function isSnoozed(notNowUntil, todayIso = localIsoDate()) {
    return Boolean(notNowUntil) && todayIso < notNowUntil;
  }

  async function computeLiveNet() {
    requireUnlocked();
    const dek = AcruetCrypto.session.getDek();
    const [positionsBody, bundles] = await Promise.all([loadPositions(dek), loadBundles(dek)]);
    const snapshotAssetCents = positionsBody
      .filter((position) => position.kind === 'ASSET')
      .reduce((sum, position) => sum + position.cents, 0);
    const liabilityCents = positionsBody
      .filter((position) => position.kind === 'LIABILITY')
      .reduce((sum, position) => sum + position.cents, 0);
    const envelopeCents = bundles.reduce((sum, bundle) => sum + bundle.totalCents, 0);
    return {
      netCents: envelopeCents + snapshotAssetCents - liabilityCents,
      envelopeCents,
      assetCents: snapshotAssetCents,
      liabilityCents,
    };
  }

  async function loadPositions(dek) {
    const response = await fetch('/positions');
    if (!response.ok) {
      throw new Error('Failed to load snapshot items.');
    }
    const body = await response.json();
    const positions = [];
    for (const position of body.positions || []) {
      const namePayload = await AcruetCrypto.decryptJson(dek, position.encryptedName);
      let cents = 0;
      if (position.latestSnapshot) {
        const snapshot = await AcruetCrypto.decryptJson(dek, position.latestSnapshot.encryptedPayload);
        cents = Number(snapshot.cents ?? snapshot.totalCents ?? 0);
      }
      positions.push({
        kind: position.kind,
        name: namePayload.name,
        cents,
      });
    }
    return positions;
  }

  async function loadBundles(dek) {
    const response = await fetch('/ledger/bundles');
    if (!response.ok) {
      throw new Error('Failed to load envelope bundles.');
    }
    const body = await response.json();
    const bundles = [];
    for (const bundle of body.bundles || []) {
      bundles.push({
        id: bundle.id,
        totalCents: await bundleTotalCents(bundle.id, dek),
      });
    }
    return bundles;
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

  async function list() {
    requireUnlocked();
    const response = await fetch('/net-worth');
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || 'Failed to load net worth.');
    }
    const dek = AcruetCrypto.session.getDek();
    const snapshots = [];
    for (const raw of body.snapshots || []) {
      try {
        const payload = await AcruetCrypto.decryptJson(dek, raw.encryptedPayload);
        snapshots.push({
          id: raw.id,
          asOfDate: raw.asOfDate,
          createdAt: raw.createdAt,
          netCents: Number(payload.netCents || 0),
          source: payload.source || '',
          envelopeCents: numberOrNull(payload.envelopeCents),
          assetCents: numberOrNull(payload.assetCents),
          liabilityCents: numberOrNull(payload.liabilityCents),
        });
      } catch (error) {
        snapshots.push({
          id: raw.id,
          asOfDate: raw.asOfDate,
          createdAt: raw.createdAt,
          decryptError: true,
        });
      }
    }
    return {
      snapshots,
      count: Number(body.count || snapshots.length),
      limit: Number(body.limit || LIMIT),
    };
  }

  async function create(asOfDate, payload) {
    requireUnlocked();
    if (!asOfDate) {
      throw new Error('As-of date is required.');
    }
    const dek = AcruetCrypto.session.getDek();
    const encryptedPayload = await AcruetCrypto.encryptJson(dek, payload);
    const response = await fetch('/net-worth', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ asOfDate, encryptedPayload }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || 'Failed to save net worth.');
    }
    return body;
  }

  return {
    LIMIT,
    SOURCE_COMPUTED,
    SOURCE_MANUAL,
    localIsoDate,
    formatCents,
    parseUsd,
    sourceLabel,
    computedPayload,
    manualPayload,
    latest,
    list,
    create,
    computeLiveNet,
    isStale,
    snoozeUntil,
    isSnoozed,
  };
})();
