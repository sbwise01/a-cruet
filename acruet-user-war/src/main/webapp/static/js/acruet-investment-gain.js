/**
 * Household investment gain over time — principal × trailing annualized return (PRODUCT.md §16).
 */
document.addEventListener('DOMContentLoaded', () => {
  const panel = document.getElementById('reportInvestmentGainPanel');
  if (!panel) {
    return;
  }

  const PROJECT_KEY = 'acruet.reports.investmentGainProject';
  const state = { items: [] };
  const els = {
    meta: document.getElementById('investmentGainMeta'),
    hint: document.getElementById('investmentGainProjectHint'),
    toggle: document.getElementById('investmentGainProjectToggle'),
    control: document.getElementById('investmentGainProjectControl'),
    table: document.getElementById('investmentGainTable'),
    wrap: document.querySelector('#reportInvestmentGainPanel .report-chart-wrap'),
    canvas: document.getElementById('investmentGainChart'),
    error: document.getElementById('reportError'),
  };
  let chartInstance = null;
  let showQueued = false;
  let history = [];
  let gate = { ready: false };

  window.AcruetInvestmentGain = {
    show: () => {
      showQueued = true;
      void show();
    },
    onKeyLocked: () => {
      showQueued = false;
      destroyChart();
    },
  };

  if (els.toggle) {
    els.toggle.addEventListener('change', () => {
      window.localStorage.setItem(PROJECT_KEY, els.toggle.checked ? '1' : '0');
      renderChart();
    });
  }

  async function show() {
    hideError();
    try {
      document.querySelector('main.page')?.classList.toggle('page--report-chart', true);
      await loadFlagged();
      history = buildHistory(state.items);
      gate = window.AcruetProjection
        ? AcruetProjection.householdInvestmentGate(state.items)
        : { ready: false, quarters: [], present: [] };
      syncToggle();
      if (els.meta) {
        els.meta.textContent = state.items.length
          ? 'All flagged investments, summed. Annualized gain is this pile × trailing return, as if you stopped contributing.'
          : 'Track an asset as an investment on Assets to see household gain.';
      }
      renderTable(history);
      renderChart();
    } catch (error) {
      showError(error.message || 'Failed to load investment gain.');
    }
  }

  async function loadFlagged() {
    const response = await fetch('/positions');
    if (!response.ok) {
      throw new Error('Failed to load snapshot items.');
    }
    const dek = AcruetCrypto.session.getDek();
    const body = await response.json();
    const items = [];
    for (const position of body.positions || []) {
      if (position.kind !== 'ASSET' || !position.investment) {
        continue;
      }
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
        snapshots,
      });
    }
    state.items = items;
  }

  function buildHistory(items) {
    const lists = items.map((item) => item.snapshots).filter((list) => list.length);
    if (!lists.length) {
      return [];
    }
    let earliest = lists[0][0].asOfDate;
    lists.forEach((list) => {
      if (list[0].asOfDate < earliest) {
        earliest = list[0].asOfDate;
      }
    });
    const months = monthEnds(earliest);
    const rows = [];
    months.forEach((monthEnd, index) => {
      const emv = sumOnOrBefore(lists, monthEnd) || 0;
      const prevEnd = index === 0 ? null : months[index - 1];
      const bmv = prevEnd ? sumOnOrBefore(lists, prevEnd) : null;
      const flow = sumFlowInMonth(lists, monthEnd);
      const hasSnapshot = items.some((item) =>
        item.snapshots.some((snapshot) => snapshot.asOfDate.slice(0, 7) === monthEnd.slice(0, 7)));
      const period = bmv == null ? null : emv - bmv - flow;
      const monthlyPct = bmv != null && bmv > 0 && period != null ? period / bmv : null;
      rows.push({
        asOfDate: monthEnd,
        periodGain: period,
        monthlyPct,
        hasSnapshot,
        nonzero: period != null && period !== 0,
        emv,
        annualizedPct: null,
        annualizedGain: null,
      });
    });
    rows.forEach((row, index) => {
      row.annualizedPct = trailingAnnualizedPct(rows, index);
      row.annualizedGain = row.annualizedPct == null
        ? null
        : Math.round(row.emv * row.annualizedPct);
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
    const start = `${monthEnd.slice(0, 7)}-01`;
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
    while (year < now.getFullYear() || (year === now.getFullYear() && month <= now.getMonth() + 1)) {
      months.push(localIsoDate(new Date(year, month, 0)));
      month += 1;
      if (month > 12) {
        month = 1;
        year += 1;
      }
    }
    return months;
  }

  function syncToggle() {
    if (!els.toggle || !els.control || !window.AcruetProjection) {
      return;
    }
    els.toggle.disabled = !gate.ready;
    els.control.title = gate.ready ? '' : AcruetProjection.TITLE_LOCKED;
    if (els.hint) {
      els.hint.textContent = gate.ready
        ? 'If you stopped contributing — income on this pile, not a prediction.'
        : AcruetProjection.scoreboardHtml(gate.quarters || [], gate.present || [], '');
    }
    if (!gate.ready) {
      els.toggle.checked = false;
    } else if (window.localStorage.getItem(PROJECT_KEY) === '1') {
      els.toggle.checked = true;
    }
  }

  function renderTable(series) {
    if (!els.table) {
      return;
    }
    const rows = series.filter((point) => point.hasSnapshot || point.nonzero);
    if (!rows.length) {
      els.table.innerHTML = '<p class="hint">No investment gain history yet.</p>';
      return;
    }
    const body = rows.slice().reverse().map((point) => {
      const cls = point.periodGain != null && point.periodGain < 0 ? ' class="report-amount-negative"' : '';
      const annualCls = point.annualizedGain != null && point.annualizedGain < 0
        ? ' class="report-amount-negative"'
        : '';
      const period = point.periodGain == null ? '—' : formatCents(point.periodGain);
      const annual = point.annualizedGain == null ? '—' : formatCents(point.annualizedGain);
      return `<tr>
        <td>${escapeHtml(point.asOfDate)}</td>
        <td${cls}>${escapeHtml(period)}</td>
        <td${annualCls}>${escapeHtml(annual)}</td>
      </tr>`;
    }).join('');
    els.table.innerHTML = `
      <table>
        <thead>
          <tr><th>Month</th><th>Gain</th><th>Annualized gain</th></tr>
        </thead>
        <tbody>${body}</tbody>
      </table>`;
  }

  function renderChart() {
    destroyChart();
    if (!els.canvas || !els.wrap || typeof Chart === 'undefined') {
      return;
    }
    if (!history.length) {
      els.wrap.hidden = true;
      return;
    }
    if (!history.some((point) => point.annualizedGain != null) && !(els.toggle && els.toggle.checked)) {
      els.wrap.hidden = true;
      return;
    }
    els.wrap.hidden = false;
    const historyPoints = history
      .filter((point) => point.annualizedGain != null)
      .map((point) => ({
        x: Date.parse(`${point.asOfDate}T12:00:00Z`),
        y: point.annualizedGain / 100,
      }));
    const datasets = [{
      label: 'Annualized gain',
      data: historyPoints,
      borderColor: '#38bdf8',
      fill: false,
      tension: 0,
      pointRadius: 3,
    }];
    if (els.toggle && els.toggle.checked && gate.ready && window.AcruetProjection) {
      const run = AcruetProjection.investmentRunRateFromLists(state.items.map((item) => item.snapshots));
      const last = history[history.length - 1];
      const rate = last.annualizedPct != null ? last.annualizedPct : run.r * 4;
      const projected = AcruetProjection.projectInvestmentValue(last.emv, 0, run.r);
      datasets.push({
        label: 'If you stopped contributing',
        data: projected.map((point) => ({
          x: Date.parse(`${point.asOfDate}T12:00:00Z`),
          y: (point.cents * rate) / 100,
        })),
        borderColor: '#94a3b8',
        borderDash: [6, 4],
        fill: false,
        tension: 0,
        pointRadius: 2,
      });
    }
    chartInstance = new Chart(els.canvas, {
      type: 'line',
      data: { datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        parsing: false,
        plugins: { legend: { labels: { color: '#e2e8f0' } } },
        scales: {
          x: {
            type: 'linear',
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
                return `$${Number(value).toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
              },
            },
            grid: { color: 'rgba(148, 163, 184, 0.15)' },
          },
        },
      },
    });
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
    return `${sign}$${Math.abs(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  function localIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
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
