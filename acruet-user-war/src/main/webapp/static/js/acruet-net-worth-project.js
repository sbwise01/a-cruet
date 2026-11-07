/**
 * Optional 5-year dashed overlay on the net-worth report (PRODUCT.md §15).
 */
window.AcruetNetWorthProject = (() => {
  const PROJECT_KEY = 'acruet.reports.netWorthProject';
  let cachedItems = null;

  function elements() {
    return {
      toggle: document.getElementById('netWorthProjectToggle'),
      control: document.getElementById('netWorthProjectControl'),
      hint: document.getElementById('netWorthProjectHint'),
    };
  }

  async function show() {
    const els = elements();
    if (!els.toggle || !window.AcruetProjection) {
      return;
    }
    cachedItems = await loadItems();
    const gate = AcruetProjection.gateForItems(cachedItems);
    els.toggle.disabled = !gate.ready;
    els.control.title = gate.ready ? '' : AcruetProjection.TITLE_LOCKED;
    const present = (gate.best && gate.best.rows)
      ? gate.best.rows.map((row) => row.present)
      : (gate.quarters || []).map(() => false);
    const closest = gate.best && gate.best.item ? gate.best.item.name : '';
    if (els.hint) {
      els.hint.textContent = gate.ready
        ? 'If this pace continued — not a prediction.'
        : AcruetProjection.scoreboardHtml(gate.quarters, present, closest);
    }
    if (!els.toggle.dataset.wired) {
      els.toggle.dataset.wired = '1';
      els.toggle.addEventListener('change', () => {
        window.localStorage.setItem(PROJECT_KEY, els.toggle.checked ? '1' : '0');
        if (window.AcruetReports && window.AcruetReports.showReportsEntry) {
          window.AcruetReports.showReportsEntry();
        }
      });
    }
    if (!gate.ready) {
      els.toggle.checked = false;
      return;
    }
    els.toggle.checked = window.localStorage.getItem(PROJECT_KEY) === '1';
  }

  async function overlayPoints() {
    const els = elements();
    if (!els.toggle || !els.toggle.checked || !window.AcruetProjection || !window.AcruetNetWorth) {
      return [];
    }
    const items = cachedItems || await loadItems();
    const live = await AcruetNetWorth.computeLiveNet();
    const quarters = AcruetProjection.trailingQuarters();
    let envelopes = live.envelopeCents;
    const nonInv = items
      .filter((item) => item.kind === 'ASSET' && !item.investment)
      .reduce((sum, item) => sum + latestCents(item), 0);
    const invStates = items.filter((item) => item.investment).map((item) => {
      const run = AcruetProjection.investmentRunRate(item.snapshots, quarters);
      return { value: latestCents(item), c: run.c, r: run.r };
    });
    const liabStates = items.filter((item) => item.kind === 'LIABILITY').map((item) => {
      const run = AcruetProjection.liabilityRunRate(item.snapshots, quarters);
      return { value: latestCents(item), delta: run.payingDown ? run.delta : 0 };
    });
    const dates = AcruetProjection.futureQuarterEnds(AcruetProjection.localIsoDate());
    return dates.map((asOfDate) => {
      invStates.forEach((item) => {
        const gain = Math.round(item.value * item.r);
        item.value = Math.max(0, item.value + item.c + gain);
      });
      liabStates.forEach((item) => {
        item.value = Math.max(0, item.value + item.delta);
      });
      const inv = invStates.reduce((sum, item) => sum + item.value, 0);
      const liab = liabStates.reduce((sum, item) => sum + item.value, 0);
      return {
        asOfDate,
        netCents: envelopes + nonInv + inv - liab,
      };
    });
  }

  function latestCents(item) {
    const snapshots = item.snapshots || [];
    if (!snapshots.length) {
      return 0;
    }
    return snapshots[snapshots.length - 1].cents || 0;
  }

  async function loadItems() {
    const response = await fetch('/positions');
    if (!response.ok) {
      throw new Error('Failed to load snapshot items.');
    }
    const dek = AcruetCrypto.session.getDek();
    const body = await response.json();
    const items = [];
    for (const position of body.positions || []) {
      const namePayload = await AcruetCrypto.decryptJson(dek, position.encryptedName);
      const snapshotsResponse = await fetch(`/positions/${position.id}/snapshots`);
      if (!snapshotsResponse.ok) {
        throw new Error('Failed to load snapshot history.');
      }
      const snapshotsBody = await snapshotsResponse.json();
      const snapshots = [];
      for (const raw of snapshotsBody.snapshots || []) {
        const payload = await AcruetCrypto.decryptJson(dek, raw.encryptedPayload);
        snapshots.push({
          asOfDate: raw.asOfDate,
          createdAt: raw.createdAt || '',
          cents: Number(payload.cents ?? payload.totalCents ?? 0),
          flowCents: Number(payload.flowCents || 0),
        });
      }
      snapshots.sort((left, right) => String(left.asOfDate).localeCompare(String(right.asOfDate)));
      items.push({
        id: position.id,
        name: namePayload.name || 'Untitled',
        kind: position.kind,
        investment: Boolean(position.investment),
        snapshots,
      });
    }
    return items;
  }

  function onKeyLocked() {
    cachedItems = null;
  }

  return { show, overlayPoints, onKeyLocked };
})();
