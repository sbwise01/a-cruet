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
    if (!window.AcruetCrypto || !AcruetCrypto.session.isUnlocked()) {
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
    list,
    create,
  };
})();
