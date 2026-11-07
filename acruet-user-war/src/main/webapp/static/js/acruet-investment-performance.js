/**
 * Investment performance — trailing 12-month annualized simple return (PRODUCT.md §14).
 */
document.addEventListener('DOMContentLoaded', () => {
  const panel = document.getElementById('reportInvestmentPerformancePanel');
  if (!panel) {
    return;
  }

  const SERIES_KEY = 'acruet.reports.investmentPerformanceSeries';
  const state = { positions: [], groups: [], snapshotCache: {} };
  const els = {
    select: document.getElementById('investmentPerformanceSelect'),
    meta: document.getElementById('investmentPerformanceMeta'),
    table: document.getElementById('investmentPerformanceTable'),
    wrap: document.querySelector('#reportInvestmentPerformancePanel .report-chart-wrap'),
    canvas: document.getElementById('investmentPerformanceChart'),
    error: document.getElementById('reportError'),
  };
  let chartInstance = null;
  let showQueued = false;

  window.AcruetInvestmentPerformance = {
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
      if (els.select.value) {
        window.localStorage.setItem(SERIES_KEY, els.select.value);
      }
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
      showError(error.message || 'Failed to load investment performance.');
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
        investment: Boolean(position.investment),
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
    state.positions = positions;
    state.groups = groups;
  }

  function flaggedAssets() {
    return state.positions.filter((item) => item.kind === 'ASSET' && item.investment);
  }

  function flaggedGroups() {
    return state.groups.filter((group) =>
      state.positions.some((item) => item.groupId === group.id && item.investment));
  }

  function populateSelect() {
    if (!els.select) {
      return;
    }
    const assets = flaggedAssets();
    const groups = flaggedGroups();
    const parts = [];
    if (assets.length) {
      parts.push(`<optgroup label="Assets">${assets.map((item) =>
        `<option value="asset:${item.id}">${escapeHtml(item.name)}</option>`).join('')}</optgroup>`);
    }
    if (groups.length) {
      parts.push(`<optgroup label="Groups">${groups.map((group) =>
        `<option value="group:${group.id}">${escapeHtml(group.name)}</option>`).join('')}</optgroup>`);
    }
    if (!parts.length) {
      els.select.innerHTML = '<option value="">No investments flagged yet</option>';
      els.select.disabled = true;
      if (els.meta) {
        els.meta.textContent = 'Track an asset as an investment on Assets, then update value plus net contribution.';
      }
      if (els.table) {
        els.table.innerHTML = '';
      }
      destroyChart();
      return;
    }
    els.select.disabled = false;
    els.select.innerHTML = parts.join('');
    const preferred = window.localStorage.getItem(SERIES_KEY);
    if (preferred && [...els.select.options].some((option) => option.value === preferred)) {
      els.select.value = preferred;
    }
  }

  async function renderSelected() {
    if (!showQueued || !els.select || !els.select.value || els.select.disabled) {
      return;
    }
    hideError();
    destroyChart();
    const series = await seriesFor(els.select.value);
    const label = els.select.selectedOptions[0] ? els.select.selectedOptions[0].textContent : 'Investment';
    if (els.meta) {
      els.meta.textContent = series.length
        ? `${series.length} months · trailing 12-month annualized return, contributions stripped`
        : 'Need two month-ends so there is a beginning value.';
    }
    renderTable(series);
    renderChart(series, label);
  }

  async function seriesFor(key) {
    const [kind, id] = key.split(':');
    const members = kind === 'group'
      ? state.positions.filter((item) => item.groupId === id && item.investment)
      : state.positions.filter((item) => item.id === id && item.investment);
    const lists = [];
    for (const member of members) {
      lists.push(await loadSnapshots(member.id));
    }
    return monthlyPerformance(lists);
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
        flowCents: Number(payload.flowCents || 0),
      });
    }
    snapshots.sort((left, right) => String(left.asOfDate).localeCompare(String(right.asOfDate)));
    state.snapshotCache[positionId] = snapshots;
    return snapshots;
  }

  function monthlyPerformance(memberLists) {
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
    const months = monthEnds(earliest);
    const rows = [];
    months.forEach((monthEnd, index) => {
      const emv = sumOnOrBefore(memberLists, monthEnd);
      const prevEnd = index === 0 ? null : months[index - 1];
      const bmv = prevEnd ? sumOnOrBefore(memberLists, prevEnd) : null;
      const flow = sumFlowInMonth(memberLists, monthEnd);
      let monthly = null;
      if (bmv != null && bmv > 0 && emv != null) {
        monthly = (emv - bmv - flow) / bmv;
      }
      rows.push({
        asOfDate: monthEnd,
        cents: emv || 0,
        flowCents: flow,
        monthlyPct: monthly,
        annualizedPct: null,
      });
    });
    rows.forEach((row, index) => {
      row.annualizedPct = trailingAnnualizedPct(rows, index);
    });
    return rows;
  }

  function trailingAnnualizedPct(rows, index) {
    const windowRows = [];
    for (let cursor = index; cursor >= 0 && windowRows.length < 12; cursor -= 1) {
      if (rows[cursor].monthlyPct != null) {
        windowRows.unshift(rows[cursor]);
      }
    }
    if (!windowRows.length) {
      return null;
    }
    const sum = windowRows.reduce((total, row) => total + row.monthlyPct, 0);
    return sum * (12 / windowRows.length);
  }

  function sumOnOrBefore(lists, asOfDate) {
    let sum = 0;
    let any = false;
    lists.forEach((list) => {
      let cents = null;
      list.forEach((snapshot) => {
        if (snapshot.asOfDate <= asOfDate) {
          cents = snapshot.cents;
        }
      });
      if (cents != null) {
        any = true;
        sum += cents;
      }
    });
    return any ? sum : null;
  }

  function sumFlowInMonth(lists, monthEnd) {
    const start = monthEnd.slice(0, 8) + '01';
    let sum = 0;
    lists.forEach((list) => {
      list.forEach((snapshot) => {
        if (snapshot.asOfDate >= start && snapshot.asOfDate <= monthEnd) {
          sum += snapshot.flowCents || 0;
        }
      });
    });
    return sum;
  }

  function monthEnds(earliest) {
    const [year0, month0] = earliest.split('-').map(Number);
    const now = new Date();
    const months = [];
    let year = year0;
    let month = month0;
    const endYear = now.getFullYear();
    const endMonth = now.getMonth() + 1;
    while (year < endYear || (year === endYear && month <= endMonth)) {
      months.push(localIsoDate(new Date(year, month, 0)));
      month += 1;
      if (month > 12) {
        month = 1;
        year += 1;
      }
    }
    return months;
  }

  function renderTable(series) {
    if (!els.table) {
      return;
    }
    if (!series.length) {
      els.table.innerHTML = '<p class="hint">No investment history for this series yet.</p>';
      return;
    }
    const rows = series.slice().reverse().map((point) => {
      const monthly = point.monthlyPct == null ? '—' : formatPct(point.monthlyPct);
      const annualized = point.annualizedPct == null ? '—' : formatPct(point.annualizedPct);
      return `<tr>
        <td>${escapeHtml(point.asOfDate)}</td>
        <td>${escapeHtml(formatCents(point.cents))}</td>
        <td>${escapeHtml(formatCents(point.flowCents))}</td>
        <td>${escapeHtml(monthly)}</td>
        <td>${escapeHtml(annualized)}</td>
      </tr>`;
    }).join('');
    els.table.innerHTML = `
      <table>
        <thead>
          <tr><th>Month</th><th>Value</th><th>Net contribution</th><th>Month %</th><th>Annualized %</th></tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>`;
  }

  function renderChart(series, label) {
    if (!els.canvas || !els.wrap) {
      return;
    }
    const points = series.filter((point) => point.annualizedPct != null);
    if (!points.length || typeof Chart === 'undefined') {
      els.wrap.hidden = true;
      return;
    }
    els.wrap.hidden = false;
    const data = points.map((point) => ({
      x: Date.parse(`${point.asOfDate}T12:00:00Z`),
      y: point.annualizedPct * 100,
    }));
    chartInstance = new Chart(els.canvas, {
      type: 'line',
      data: {
        datasets: [{
          label: `${label} annualized return`,
          data,
          borderColor: '#38bdf8',
          fill: false,
          tension: 0,
          pointRadius: 3,
        }],
      },
      options: chartOptions(points, (point) => formatPct(point.annualizedPct)),
    });
  }

  function chartOptions(series, labelFn) {
    const xs = series.map((point) => Date.parse(`${point.asOfDate}T12:00:00Z`));
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const pad = Math.max((maxX - minX) * 0.03, 15 * 24 * 60 * 60 * 1000);
    return {
      responsive: true,
      maintainAspectRatio: false,
      parsing: false,
      interaction: { mode: 'nearest', intersect: false },
      plugins: {
        legend: { labels: { color: '#e2e8f0' } },
        tooltip: {
          callbacks: {
            title(items) {
              return series[items[0]?.dataIndex]?.asOfDate || '';
            },
            label(context) {
              const point = series[context.dataIndex];
              return point ? labelFn(point) : '';
            },
          },
        },
      },
      scales: {
        x: {
          type: 'linear',
          min: minX - pad,
          max: maxX + pad,
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
              return `${Number(value).toLocaleString('en-US', { maximumFractionDigits: 0 })}%`;
            },
          },
          grid: { color: 'rgba(148, 163, 184, 0.15)' },
        },
      },
    };
  }

  function formatPct(value) {
    return `${(value * 100).toLocaleString('en-US', { maximumFractionDigits: 2 })}%`;
  }

  function formatCents(cents) {
    const value = Number(cents || 0) / 100;
    const sign = value < 0 ? '-' : '';
    return `${sign}$${Math.abs(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  function localIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function destroyChart() {
    if (chartInstance) {
      chartInstance.destroy();
      chartInstance = null;
    }
    if (els.wrap) {
      els.wrap.hidden = true;
    }
  }

  function showError(message) {
    if (els.error) {
      els.error.textContent = message;
      els.error.hidden = false;
    }
  }

  function hideError() {
    if (els.error) {
      els.error.hidden = true;
      els.error.textContent = '';
    }
  }

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
});
