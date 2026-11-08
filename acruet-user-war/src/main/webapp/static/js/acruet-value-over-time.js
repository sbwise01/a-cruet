/**
 * Snapshot value-over-time report — item or group, monthly carry-forward (PRODUCT.md §13).
 */
document.addEventListener('DOMContentLoaded', () => {
  const panel = document.getElementById('reportValueOverTimePanel');
  if (!panel) {
    return;
  }

  const SERIES_STORAGE_KEY = 'acruet.reports.valueOverTimeSeries';

  const state = {
    positions: [],
    groups: [],
    snapshotCache: {},
    series: [],
  };

  const els = {
    select: document.getElementById('valueSeriesSelect'),
    meta: document.getElementById('valueOverTimeMeta'),
    table: document.getElementById('valueOverTimeTable'),
    wrap: document.querySelector('#reportValueOverTimePanel .report-chart-wrap'),
    canvas: document.getElementById('valueOverTimeChart'),
    error: document.getElementById('reportError'),
  };

  let chartInstance = null;
  let showQueued = false;

  window.AcruetValueOverTime = {
    show: () => {
      showQueued = true;
      void show();
    },
    onKeyLocked: () => {
      showQueued = false;
      destroyChart();
      state.snapshotCache = {};
    },
  };

  if (els.select) {
    els.select.addEventListener('change', () => {
      persistSelection();
      void renderSelected();
    });
  }

  async function show() {
    hideError();
    try {
      await loadCatalog();
      populateSelect();
      document.querySelector('main.page')?.classList.toggle('page--report-chart', true);
      await renderSelected();
    } catch (error) {
      showError(error.message || 'Failed to load value over time.');
    }
  }

  async function loadCatalog() {
    const [positionsResponse, groupsResponse] = await Promise.all([
      fetch('/positions'),
      fetch('/position-groups'),
    ]);
    if (!positionsResponse.ok) {
      throw new Error('Failed to load snapshot items.');
    }
    if (!groupsResponse.ok) {
      throw new Error('Failed to load groups.');
    }
    const dek = AcruetCrypto.session.getDek();
    const positionsBody = await positionsResponse.json();
    const groupsBody = await groupsResponse.json();
    const positions = [];
    for (const position of positionsBody.positions || []) {
      const namePayload = await AcruetCrypto.decryptJson(dek, position.encryptedName);
      positions.push({
        id: position.id,
        kind: position.kind,
        groupId: position.groupId || null,
        name: namePayload.name || 'Untitled',
      });
    }
    positions.sort((left, right) =>
      String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
    const groups = [];
    for (const group of groupsBody.groups || []) {
      let name = 'Untitled';
      try {
        const payload = await AcruetCrypto.decryptJson(dek, group.encryptedName);
        name = payload.name || name;
      } catch (ignored) {
        name = 'Untitled';
      }
      groups.push({ id: group.id, name });
    }
    groups.sort((left, right) =>
      String(left.name || '').localeCompare(String(right.name || ''), undefined, { sensitivity: 'base' }));
    state.positions = positions;
    state.groups = groups;
  }

  function seriesOptions() {
    const assets = state.positions.filter((item) => item.kind === 'ASSET');
    const liabilities = state.positions.filter((item) => item.kind === 'LIABILITY');
    const assetGroups = [];
    const liabilityGroups = [];
    state.groups.forEach((group) => {
      const members = state.positions.filter((item) => item.groupId === group.id);
      if (members.some((item) => item.kind === 'ASSET')) {
        assetGroups.push(group);
      }
      if (members.some((item) => item.kind === 'LIABILITY')) {
        liabilityGroups.push(group);
      }
    });
    return { assets, liabilities, assetGroups, liabilityGroups };
  }

  function populateSelect() {
    if (!els.select) {
      return;
    }
    const options = seriesOptions();
    const parts = [];
    appendOptGroup(parts, 'Assets', options.assets.map((item) => optionHtml('asset', item.id, item.name)));
    appendOptGroup(
      parts,
      'Asset groups',
      options.assetGroups.map((group) => optionHtml('asset-group', group.id, group.name)),
    );
    appendOptGroup(
      parts,
      'Liabilities',
      options.liabilities.map((item) => optionHtml('liability', item.id, item.name)),
    );
    appendOptGroup(
      parts,
      'Liability groups',
      options.liabilityGroups.map((group) => optionHtml('liability-group', group.id, group.name)),
    );
    if (!parts.length) {
      els.select.innerHTML = '<option value="">No snapshot items yet</option>';
      els.select.disabled = true;
      return;
    }
    els.select.disabled = false;
    els.select.innerHTML = parts.join('');
    const preferred = window.localStorage.getItem(SERIES_STORAGE_KEY);
    if (preferred && [...els.select.options].some((option) => option.value === preferred)) {
      els.select.value = preferred;
    }
  }

  function appendOptGroup(parts, label, options) {
    if (!options.length) {
      return;
    }
    parts.push(`<optgroup label="${escapeHtml(label)}">${options.join('')}</optgroup>`);
  }

  function optionHtml(kind, id, name) {
    return `<option value="${kind}:${id}">${escapeHtml(name)}</option>`;
  }

  function persistSelection() {
    if (!els.select || !els.select.value) {
      return;
    }
    window.localStorage.setItem(SERIES_STORAGE_KEY, els.select.value);
  }

  async function renderSelected() {
    if (!showQueued) {
      return;
    }
    hideError();
    destroyChart();
    const key = els.select ? els.select.value : '';
    if (!key) {
      if (els.meta) {
        els.meta.textContent = '';
      }
      if (els.table) {
        els.table.innerHTML = '<p class="hint">Add a snapshot asset or liability on Assets to chart it here.</p>';
      }
      if (els.wrap) {
        els.wrap.hidden = true;
      }
      return;
    }
    persistSelection();
    try {
      const series = await loadSeries(key);
      state.series = series;
      if (els.meta) {
        els.meta.textContent = series.length
          ? `${series.length} month${series.length === 1 ? '' : 's'} · last known value carried forward`
          : '';
      }
      renderTable(series);
      renderChart(series, selectedLabel());
    } catch (error) {
      if (els.meta) {
        els.meta.textContent = '';
      }
      if (els.table) {
        els.table.innerHTML = '';
      }
      showError(error.message || 'Failed to load series.');
    }
  }

  function selectedLabel() {
    if (!els.select) {
      return 'Value';
    }
    const option = els.select.selectedOptions[0];
    return option ? option.textContent : 'Value';
  }

  async function loadSeries(key) {
    const [kind, id] = splitKey(key);
    if (kind === 'asset' || kind === 'liability') {
      const snapshots = await loadSnapshots(id);
      return carryForwardSeries([snapshots]);
    }
    const wantKind = kind === 'asset-group' ? 'ASSET' : 'LIABILITY';
    const members = state.positions.filter((item) => item.groupId === id && item.kind === wantKind);
    const lists = [];
    for (const member of members) {
      lists.push(await loadSnapshots(member.id));
    }
    return carryForwardSeries(lists);
  }

  function splitKey(key) {
    const index = key.indexOf(':');
    if (index < 0) {
      return [key, ''];
    }
    return [key.slice(0, index), key.slice(index + 1)];
  }

  async function loadSnapshots(positionId) {
    if (state.snapshotCache[positionId]) {
      return state.snapshotCache[positionId];
    }
    const response = await fetch(`/positions/${positionId}/snapshots`);
    if (!response.ok) {
      throw new Error('Failed to load snapshot history.');
    }
    const body = await response.json();
    const dek = AcruetCrypto.session.getDek();
    const snapshots = [];
    for (const raw of body.snapshots || []) {
      const payload = await AcruetCrypto.decryptJson(dek, raw.encryptedPayload);
      snapshots.push({
        asOfDate: raw.asOfDate,
        createdAt: raw.createdAt || '',
        cents: Number(payload.cents ?? payload.totalCents ?? 0),
      });
    }
    const latest = latestSnapshotPerAsOfDate(snapshots);
    state.snapshotCache[positionId] = latest;
    return latest;
  }

  function latestSnapshotPerAsOfDate(snapshots) {
    const byDate = new Map();
    snapshots.forEach((snapshot) => {
      const existing = byDate.get(snapshot.asOfDate);
      if (!existing || String(snapshot.createdAt || '') >= String(existing.createdAt || '')) {
        byDate.set(snapshot.asOfDate, snapshot);
      }
    });
    return Array.from(byDate.values()).sort((left, right) =>
      String(left.asOfDate).localeCompare(String(right.asOfDate)));
  }

  function carryForwardSeries(memberLists) {
    const dated = memberLists.filter((list) => list.length);
    if (!dated.length) {
      return [];
    }
    let earliest = dated[0][0].asOfDate;
    dated.forEach((list) => {
      if (list[0].asOfDate < earliest) {
        earliest = list[0].asOfDate;
      }
    });
    const months = monthEndsInclusive(monthEndOfDate(earliest), currentMonthEndIso());
    const points = [];
    months.forEach((monthEnd) => {
      let sum = 0;
      let any = false;
      memberLists.forEach((list) => {
        const cents = latestCentsOnOrBefore(list, monthEnd);
        if (cents != null) {
          any = true;
          sum += cents;
        }
      });
      if (any) {
        points.push({ asOfDate: monthEnd, cents: sum });
      }
    });
    return points;
  }

  function latestCentsOnOrBefore(snapshots, asOfDate) {
    let cents = null;
    snapshots.forEach((snapshot) => {
      if (snapshot.asOfDate <= asOfDate) {
        cents = snapshot.cents;
      }
    });
    return cents;
  }

  function monthEndOfDate(isoDate) {
    const { year, month } = parseIsoParts(isoDate);
    return monthEndIso(year, month);
  }

  function currentMonthEndIso() {
    const now = new Date();
    const today = localIsoDate(now);
    const thisMonthEnd = monthEndIso(now.getFullYear(), now.getMonth() + 1);
    if (thisMonthEnd <= today) {
      return thisMonthEnd;
    }
    return localIsoDate(new Date(now.getFullYear(), now.getMonth(), 0));
  }

  function monthEndIso(year, month) {
    return localIsoDate(new Date(year, month, 0));
  }

  function monthEndsInclusive(startIso, endIso) {
    const start = parseIsoParts(startIso);
    const end = parseIsoParts(endIso);
    const months = [];
    let year = start.year;
    let month = start.month;
    while (year < end.year || (year === end.year && month <= end.month)) {
      months.push(monthEndIso(year, month));
      month += 1;
      if (month > 12) {
        month = 1;
        year += 1;
      }
    }
    return months;
  }

  function parseIsoParts(isoDate) {
    const [year, month, day] = String(isoDate).split('-').map(Number);
    return { year, month, day };
  }

  function localIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function renderTable(series) {
    if (!els.table) {
      return;
    }
    if (!series.length) {
      els.table.innerHTML = '<p class="hint">No snapshot history for this item yet.</p>';
      return;
    }
    const rows = series
      .slice()
      .reverse()
      .map((point) => {
        const amountClass = point.cents < 0 ? ' class="report-amount-negative"' : '';
        return `<tr>
          <td>${escapeHtml(point.asOfDate)}</td>
          <td${amountClass}>${escapeHtml(formatCents(point.cents))}</td>
        </tr>`;
      })
      .join('');
    els.table.innerHTML = `
      <table>
        <thead>
          <tr><th>As of</th><th>Value</th></tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>`;
  }

  function renderChart(series, label) {
    if (!els.canvas || !els.wrap) {
      return;
    }
    if (!series.length) {
      els.wrap.hidden = true;
      return;
    }
    if (typeof Chart === 'undefined') {
      showError('Chart library failed to load.');
      return;
    }
    els.wrap.hidden = false;
    const points = series.map((point) => ({
      x: asOfTimestamp(point.asOfDate),
      y: point.cents / 100,
    }));
    const xs = points.map((point) => point.x);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const pad = Math.max((maxX - minX) * 0.03, 15 * 24 * 60 * 60 * 1000);
    chartInstance = new Chart(els.canvas, {
      type: 'line',
      data: {
        datasets: [{
          label,
          data: points,
          borderColor: '#38bdf8',
          backgroundColor: 'rgba(56, 189, 248, 0.15)',
          fill: false,
          tension: 0,
          stepped: 'before',
          pointRadius: 3,
          spanGaps: false,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        parsing: false,
        interaction: { mode: 'nearest', intersect: false },
        plugins: {
          legend: { labels: { color: '#e2e8f0' } },
          tooltip: {
            callbacks: {
              title(items) {
                const point = series[items[0]?.dataIndex];
                return point ? point.asOfDate : '';
              },
              label(context) {
                const point = series[context.dataIndex];
                return point ? formatCents(point.cents) : '';
              },
            },
          },
        },
        scales: {
          x: {
            type: 'linear',
            min: minX - pad,
            max: maxX + pad,
            afterBuildTicks(scale) {
              scale.ticks = monthTickValues(scale.min, scale.max).map((value) => ({ value }));
            },
            ticks: {
              color: '#94a3b8',
              callback(value) {
                const date = new Date(value);
                return date.getUTCMonth() === 0 ? String(date.getUTCFullYear()) : '';
              },
            },
            grid: { color: 'rgba(148, 163, 184, 0.15)' },
          },
          y: {
            ticks: {
              color: '#94a3b8',
              callback(value) {
                return formatDollars(value);
              },
            },
            grid: { color: 'rgba(148, 163, 184, 0.15)' },
          },
        },
      },
    });
  }

  function monthTickValues(min, max) {
    const start = new Date(min);
    const end = new Date(max);
    let year = start.getUTCFullYear();
    let month = start.getUTCMonth();
    const ticks = [];
    while (year < end.getUTCFullYear() || (year === end.getUTCFullYear() && month <= end.getUTCMonth())) {
      const timestamp = Date.UTC(year, month + 1, 0, 12, 0, 0);
      if (timestamp >= min && timestamp <= max) {
        ticks.push(timestamp);
      } else if (timestamp < min) {
        ticks.push(min);
      } else if (timestamp > max) {
        ticks.push(max);
      }
      month += 1;
      if (month > 11) {
        month = 0;
        year += 1;
      }
    }
    return ticks;
  }

  function asOfTimestamp(asOfDate) {
    return Date.parse(`${asOfDate}T12:00:00Z`);
  }

  function destroyChart() {
    if (chartInstance) {
      chartInstance.destroy();
      chartInstance = null;
    }
  }

  function formatCents(cents) {
    const value = Number(cents || 0) / 100;
    const sign = value < 0 ? '-' : '';
    return `${sign}$${Math.abs(value).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  }

  function formatDollars(value) {
    const sign = value < 0 ? '-' : '';
    return `${sign}$${Math.abs(value).toFixed(2)}`;
  }

  function showError(message) {
    if (!els.error) {
      return;
    }
    els.error.textContent = message;
    els.error.hidden = false;
  }

  function hideError() {
    if (!els.error) {
      return;
    }
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
