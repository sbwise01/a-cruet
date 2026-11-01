/**
 * Client-side reports — CSV export and balance chart (Phase 10).
 */
window.AcruetReports = (() => {
  let api = null;
  let chartInstance = null;
  let netWorthChartInstance = null;
  let lastTxReport = null;

  const els = {};
  let panelWired = false;
  let controlsWired = false;

  function refreshElementRefs() {
    els.panel = document.getElementById('reportsPanel');
    els.hub = document.getElementById('reportsHub');
    els.transactionsPanel = document.getElementById('reportTransactionsPanel');
    els.chartPanel = document.getElementById('reportChartPanel');
    els.netWorthPanel = document.getElementById('reportNetWorthPanel');
    els.error = document.getElementById('reportError');
    els.txError = document.getElementById('txReportError');
  }

  function init(ledgerApi) {
    api = ledgerApi;
    refreshElementRefs();
    wirePanelActions();
    if (!els.panel) {
      return;
    }
    if (controlsWired) {
      applyBundleScopeHints();
      return;
    }
    controlsWired = true;

    bindButton('btnRenderChart', renderBalanceChart);

    setDefaultDates(document.getElementById('txReportFrom'), document.getElementById('txReportTo'), 'year');
    setDefaultDates(document.getElementById('chartReportFrom'), document.getElementById('chartReportTo'), 'month');
  }

  function wirePanelActions() {
    if (panelWired) {
      return;
    }
    const panel = document.getElementById('reportsPanel');
    if (!panel) {
      return;
    }
    panelWired = true;
    panel.addEventListener('click', (event) => {
      const button = event.target.closest('button[id]');
      if (!button || !panel.contains(button)) {
        return;
      }
      if (button.id === 'btnShowTxReport') {
        event.preventDefault();
        void showTransactionsReport();
        return;
      }
      if (button.id === 'btnDownloadTxCsv') {
        event.preventDefault();
        void downloadTransactionsCsv();
      }
    });
  }

  function bindButton(id, handler) {
    const button = document.getElementById(id);
    if (button) {
      button.addEventListener('click', handler);
    }
  }

  function normalizeAccountId(id) {
    return String(id ?? '').trim().toLowerCase();
  }

  function safeEscapeHtml(value) {
    const text = value == null ? '' : String(value);
    if (api && typeof api.escapeHtml === 'function') {
      return api.escapeHtml(text);
    }
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function setDefaultDates(fromInput, toInput, range = 'month') {
    if (!fromInput || !toInput) {
      return;
    }
    const today = new Date();
    toInput.value = localIsoDate(today);
    if (range === 'year') {
      fromInput.value = localIsoDate(new Date(today.getFullYear(), 0, 1));
      return;
    }
    fromInput.value = localIsoDate(new Date(today.getFullYear(), today.getMonth(), 1));
  }

  function setChartLayoutActive(active) {
    document.querySelector('main.page')?.classList.toggle('page--report-chart', active);
  }

  function applyBundleScopeHints() {
    const allBundles = api && typeof api.isAllBundles === 'function' && api.isAllBundles();
    const name = api && typeof api.getBundleName === 'function' ? api.getBundleName() : '';
    const text = allBundles
      ? 'Reporting on all bundles. Switch bundles above to report on one.'
      : name
        ? `Reporting on bundle “${name}”. Switch bundles above to report on another.`
        : 'Reporting on the selected bundle.';
    document.querySelectorAll('.reports-bundle-hint').forEach((hint) => {
      hint.textContent = text;
    });
  }

  function showReportsEntry() {
    if (!els.panel || !api) {
      return;
    }
    api.beforeReportsOpen();
    els.panel.hidden = false;
    applyBundleScopeHints();
    if (els.transactionsPanel) {
      populateAccountList('txReportAccounts');
      setChartLayoutActive(false);
      safeClearTxReport();
    } else if (els.chartPanel) {
      populateAccountList('chartReportAccounts');
      setChartLayoutActive(true);
      destroyChart();
    } else if (els.netWorthPanel) {
      setChartLayoutActive(true);
      void renderNetWorthReport();
    }
  }

  function closeAll() {
    hideError();
    setChartLayoutActive(false);
    destroyChart();
    destroyNetWorthChart();
    if (api) {
      api.afterReportsClose();
    }
  }

  function populateAccountList(containerId) {
    const container = document.getElementById(containerId);
    const accounts = api.getAccounts();
    if (accounts.length === 0) {
      container.innerHTML = '<p class="hint">Create at least one envelope first.</p>';
      return;
    }
    const rows = accounts
      .map(
        (account) => `
          <label>
            <input type="checkbox" class="report-account-cb" value="${account.id}" checked>
            ${api.escapeHtml(account.name)}
          </label>`,
      )
      .join('');
    container.innerHTML = `
      <p class="hint">Envelopes to include</p>
      ${rows}
      <label><input type="checkbox" id="${containerId}All" checked> All envelopes</label>`;
    const allCheckbox = document.getElementById(`${containerId}All`);
    allCheckbox.addEventListener('change', () => {
      container.querySelectorAll('.report-account-cb').forEach((box) => {
        box.checked = allCheckbox.checked;
      });
    });
    container.querySelectorAll('.report-account-cb').forEach((box) => {
      box.addEventListener('change', () => {
        const boxes = Array.from(container.querySelectorAll('.report-account-cb'));
        allCheckbox.checked = boxes.every((item) => item.checked);
      });
    });
  }

  function selectedAccountIds(containerId) {
    return Array.from(document.querySelectorAll(`#${containerId} .report-account-cb:checked`))
      .map((box) => box.value);
  }

  async function loadTransactions(from, to) {
    const bundleId = api && typeof api.getBundleId === 'function' ? api.getBundleId() : null;
    if (bundleId) {
      return fetchTransactions(from, to, bundleId);
    }
    const bundleIds = api && typeof api.getBundleIds === 'function' ? api.getBundleIds() : [];
    if (!bundleIds.length) {
      return fetchTransactions(from, to, null);
    }
    const batches = await Promise.all(bundleIds.map((id) => fetchTransactions(from, to, id)));
    return batches.flat();
  }

  async function fetchTransactions(from, to, bundleId) {
    const params = new URLSearchParams({ from, to });
    if (bundleId) {
      params.set('bundleId', bundleId);
    }
    const response = await fetch(`/ledger/transactions?${params.toString()}`);
    if (!response.ok) {
      throw new Error('Failed to load transactions for report.');
    }
    const body = await response.json();
    const raw = Array.isArray(body.transactions) ? body.transactions : [];
    return api.decryptTransactions(raw);
  }

  function safeClearTxReport() {
    lastTxReport = null;
    const results = document.getElementById('txReportResults');
    if (results) {
      results.hidden = true;
      results.innerHTML = '';
    }
    hideTxError();
  }

  function readTxReportInputs() {
    if (api.requireUnlocked) {
      api.requireUnlocked();
    }
    const accounts = api.getAccounts();
    if (accounts.length === 0) {
      throw new Error('Create at least one envelope first.');
    }
    const selectedIds = new Set(
      selectedAccountIds('txReportAccounts').map(normalizeAccountId),
    );
    if (selectedIds.size === 0) {
      throw new Error('Select at least one envelope.');
    }
    const from = document.getElementById('txReportFrom').value;
    const to = document.getElementById('txReportTo').value;
    if (!from || !to || from > to) {
      throw new Error('Enter a valid date range.');
    }
    return { accounts, selectedIds, from, to };
  }

  async function buildTransactionRows(accounts, selectedIds, from, to) {
    const accountNames = new Map(
      accounts.map((account) => [normalizeAccountId(account.id), account.name]),
    );
    const transactions = await loadTransactions(from, to);
    const rows = [['Date', 'Type', 'Memo', 'Envelope', 'Amount']];
    transactions
      .slice()
      .sort(compareTransactions)
      .forEach((transaction) => {
        const payload = transaction.payload || {};
        const lines = Array.isArray(payload.lines) ? payload.lines : [];
        lines.forEach((line) => {
          const accountId = normalizeAccountId(line.accountId);
          if (!selectedIds.has(accountId)) {
            return;
          }
          rows.push([
            transaction.transactionDate,
            transaction.transactionType,
            payload.memo || '',
            accountNames.get(accountId) || '',
            line.amountCents,
          ]);
        });
      });
    if (rows.length === 1) {
      throw new Error('No transactions found for that range and envelope selection.');
    }
    return rows;
  }

  async function showTransactionsReport() {
    const showBtn = document.getElementById('btnShowTxReport');
    if (showBtn) {
      showBtn.disabled = true;
      showBtn.textContent = 'Loading…';
    }
    hideError();
    hideTxError();
    try {
      if (!api) {
        throw new Error('Reports are still loading. Wait a moment, then try again.');
      }
      const { accounts, selectedIds, from, to } = readTxReportInputs();
      const rows = await buildTransactionRows(accounts, selectedIds, from, to);
      lastTxReport = { rows, from, to };
      renderTxReportTable(rows);
    } catch (error) {
      safeClearTxReport();
      showTxError(error.message || 'Failed to load report.');
      console.error(error);
    } finally {
      if (showBtn) {
        showBtn.disabled = false;
        showBtn.textContent = 'Show report';
      }
    }
  }

  function showTxReportClick(event) {
    if (event) {
      event.preventDefault();
    }
    void showTransactionsReport();
    return false;
  }

  function downloadTxCsvClick(event) {
    if (event) {
      event.preventDefault();
    }
    void downloadTransactionsCsv();
    return false;
  }

  function rowsToCsv(rows) {
    return rows.map((row, index) => {
      if (index === 0) {
        return row;
      }
      return [
        row[0],
        row[1],
        row[2],
        row[3],
        formatCsvAmount(row[4]),
      ];
    });
  }

  async function downloadTransactionsCsv() {
    const downloadBtn = document.getElementById('btnDownloadTxCsv');
    if (downloadBtn) {
      downloadBtn.disabled = true;
      downloadBtn.textContent = 'Preparing…';
    }
    hideError();
    hideTxError();
    try {
      if (!api) {
        throw new Error('Reports are still loading. Wait a moment, then try again.');
      }
      const { accounts, selectedIds, from, to } = readTxReportInputs();
      const rows = await buildTransactionRows(accounts, selectedIds, from, to);
      downloadCsv(
        `acruet-transactions-${from}-to-${to}.csv`,
        rowsToCsv(rows),
      );
    } catch (error) {
      showTxError(error.message || 'Failed to download CSV.');
      console.error(error);
    } finally {
      if (downloadBtn) {
        downloadBtn.disabled = false;
        downloadBtn.textContent = 'Download CSV';
      }
    }
  }

  function renderTxReportTable(rows) {
    const header = rows[0];
    const bodyRows = rows.slice(1);
    const results = document.getElementById('txReportResults');
    if (!results) {
      return;
    }
    const tableRows = bodyRows
      .map((row) => {
        const amountClass = row[4] < 0 ? ' class="report-amount-negative"' : '';
        const amount = formatCents(row[4]);
        return `<tr>
          <td>${safeEscapeHtml(row[0])}</td>
          <td>${safeEscapeHtml(row[1])}</td>
          <td>${safeEscapeHtml(row[2])}</td>
          <td>${safeEscapeHtml(row[3])}</td>
          <td${amountClass}>${safeEscapeHtml(amount)}</td>
        </tr>`;
      })
      .join('');
    const lineLabel = bodyRows.length === 1 ? 'line' : 'lines';
    results.innerHTML = `
      <p class="hint">${bodyRows.length} transaction ${lineLabel}</p>
      <table>
        <thead>
          <tr>${header.map((cell) => `<th>${safeEscapeHtml(cell)}</th>`).join('')}</tr>
        </thead>
        <tbody>${tableRows}</tbody>
      </table>`;
    results.hidden = false;
  }

  function formatCents(cents) {
    const sign = cents < 0 ? '-' : '';
    const abs = Math.abs(cents);
    return `${sign}$${(abs / 100).toFixed(2)}`;
  }

  async function renderBalanceChart() {
    hideError();
    destroyChart();
    try {
      const accounts = api.getAccounts().filter((account) =>
        selectedAccountIds('chartReportAccounts').includes(account.id),
      );
      if (accounts.length === 0) {
        throw new Error('Select at least one envelope.');
      }
      const from = document.getElementById('chartReportFrom').value;
      const to = document.getElementById('chartReportTo').value;
      if (!from || !to || from > to) {
        throw new Error('Enter a valid date range.');
      }
      const historyFrom = '1970-01-01';
      const transactions = await loadTransactions(historyFrom, to);
      const dates = listDates(from, to);
      const balancesByDate = computeDailyBalances(accounts, transactions, dates);
      const palette = ['#38bdf8', '#34d399', '#fbbf24', '#f472b6', '#a78bfa', '#fb923c', '#22d3ee', '#4ade80'];
      const datasets = accounts.map((account, index) => ({
        label: account.name,
        data: dates.map((date) => (balancesByDate.get(date)?.get(account.id) || 0) / 100),
        borderColor: palette[index % palette.length],
        backgroundColor: withAlpha(palette[index % palette.length], 0.45),
        fill: true,
        tension: 0.25,
        pointRadius: 0,
      }));
      const canvas = document.getElementById('balanceChart');
      if (typeof Chart === 'undefined') {
        throw new Error('Chart library failed to load.');
      }
      chartInstance = new Chart(canvas, {
        type: 'line',
        data: { labels: dates, datasets },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: { mode: 'index', intersect: false },
          plugins: {
            legend: { labels: { color: '#e2e8f0' } },
            tooltip: {
              callbacks: {
                label(context) {
                  return `${context.dataset.label}: ${formatDollars(context.parsed.y)}`;
                },
              },
            },
          },
          scales: {
            x: {
              ticks: { color: '#94a3b8', maxTicksLimit: 10 },
              grid: { color: 'rgba(148, 163, 184, 0.15)' },
            },
            y: {
              stacked: true,
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
    } catch (error) {
      showError(error.message || 'Failed to render chart.');
      console.error(error);
    }
  }

  function computeDailyBalances(accounts, transactions, dates) {
    const accountIds = new Set(accounts.map((account) => account.id));
    const running = new Map();
    accountIds.forEach((id) => running.set(id, 0));
    const sorted = transactions.slice().sort(compareTransactions);
    const firstDate = dates[0];
    let txIndex = 0;

    while (txIndex < sorted.length && sorted[txIndex].transactionDate < firstDate) {
      applyTransaction(sorted[txIndex], accountIds, running);
      txIndex += 1;
    }

    const byDate = new Map();
    dates.forEach((date) => {
      while (txIndex < sorted.length && sorted[txIndex].transactionDate === date) {
        applyTransaction(sorted[txIndex], accountIds, running);
        txIndex += 1;
      }
      byDate.set(date, new Map(running));
    });
    return byDate;
  }

  function applyTransaction(transaction, accountIds, running) {
    transaction.payload.lines.forEach((line) => {
      if (accountIds.has(line.accountId)) {
        running.set(line.accountId, (running.get(line.accountId) || 0) + line.amountCents);
      }
    });
  }

  function compareTransactions(a, b) {
    if (a.transactionDate !== b.transactionDate) {
      return a.transactionDate < b.transactionDate ? -1 : 1;
    }
    return String(a.createdAt || '').localeCompare(String(b.createdAt || ''));
  }

  function listDates(from, to) {
    const dates = [];
    const cursor = new Date(`${from}T12:00:00`);
    const end = new Date(`${to}T12:00:00`);
    while (cursor <= end) {
      dates.push(localIsoDate(cursor));
      cursor.setDate(cursor.getDate() + 1);
    }
    return dates;
  }

  function localIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function formatCsvAmount(cents) {
    return (cents / 100).toFixed(2);
  }

  function formatDollars(value) {
    const sign = value < 0 ? '-' : '';
    return `${sign}$${Math.abs(value).toFixed(2)}`;
  }

  function withAlpha(hex, alpha) {
    const normalized = hex.replace('#', '');
    const r = Number.parseInt(normalized.slice(0, 2), 16);
    const g = Number.parseInt(normalized.slice(2, 4), 16);
    const b = Number.parseInt(normalized.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }

  function downloadCsv(filename, rows) {
    const csv = rows.map((row) => row.map(escapeCsvCell).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  function escapeCsvCell(value) {
    const text = value == null ? '' : String(value);
    if (/[",\n]/.test(text)) {
      return `"${text.replace(/"/g, '""')}"`;
    }
    return text;
  }

  function destroyChart() {
    if (chartInstance) {
      chartInstance.destroy();
      chartInstance = null;
    }
  }

  function destroyNetWorthChart() {
    if (netWorthChartInstance) {
      netWorthChartInstance.destroy();
      netWorthChartInstance = null;
    }
  }

  async function renderNetWorthReport() {
    hideError();
    destroyNetWorthChart();
    const meta = document.getElementById('netWorthMeta');
    const table = document.getElementById('netWorthTable');
    if (!window.AcruetNetWorth) {
      showError('Net worth helper failed to load.');
      return;
    }
    try {
      api.requireUnlocked();
      const result = await AcruetNetWorth.list();
      const usable = result.snapshots.filter((snapshot) => !snapshot.decryptError);
      if (meta) {
        meta.textContent = `${result.count} of ${result.limit} observations`;
      }
      renderNetWorthTable(result.snapshots);
      renderNetWorthChart(usable);
    } catch (error) {
      if (meta) {
        meta.textContent = '';
      }
      if (table) {
        table.innerHTML = '';
      }
      showError(error.message || 'Failed to load net worth.');
    }
  }

  function renderNetWorthTable(snapshots) {
    const table = document.getElementById('netWorthTable');
    if (!table) {
      return;
    }
    if (!snapshots.length) {
      table.innerHTML = '<p class="hint">No saved net worth yet. Save current or add a historical total from Assets.</p>';
      return;
    }
    const rows = snapshots
      .slice()
      .reverse()
      .map((snapshot) => {
        if (snapshot.decryptError) {
          return `<tr><td>${safeEscapeHtml(snapshot.asOfDate)}</td><td colspan="2">Unable to decrypt</td></tr>`;
        }
        const title = netWorthBreakdownTitle(snapshot);
        const titleAttr = title ? ` title="${safeEscapeHtml(title)}"` : '';
        const netClass = snapshot.netCents < 0 ? ' class="report-amount-negative"' : '';
        return `<tr${titleAttr}>
          <td>${safeEscapeHtml(snapshot.asOfDate)}</td>
          <td${netClass}>${safeEscapeHtml(AcruetNetWorth.formatCents(snapshot.netCents))}</td>
          <td>${safeEscapeHtml(AcruetNetWorth.sourceLabel(snapshot.source))}</td>
        </tr>`;
      })
      .join('');
    table.innerHTML = `
      <table>
        <thead>
          <tr><th>As of</th><th>Net</th><th>Source</th></tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>`;
  }

  function netWorthBreakdownTitle(snapshot) {
    if (snapshot.source !== AcruetNetWorth.SOURCE_COMPUTED) {
      return '';
    }
    const parts = [];
    if (snapshot.envelopeCents != null) {
      parts.push(`Envelopes ${AcruetNetWorth.formatCents(snapshot.envelopeCents)}`);
    }
    if (snapshot.assetCents != null) {
      parts.push(`Assets ${AcruetNetWorth.formatCents(snapshot.assetCents)}`);
    }
    if (snapshot.liabilityCents != null) {
      parts.push(`Liabilities ${AcruetNetWorth.formatCents(snapshot.liabilityCents)}`);
    }
    return parts.join(' · ');
  }

  function renderNetWorthChart(snapshots) {
    const canvas = document.getElementById('netWorthChart');
    if (!canvas) {
      return;
    }
    const wrap = canvas.closest('.report-chart-wrap');
    if (!snapshots.length) {
      if (wrap) {
        wrap.hidden = true;
      }
      return;
    }
    if (wrap) {
      wrap.hidden = false;
    }
    if (typeof Chart === 'undefined') {
      showError('Chart library failed to load.');
      return;
    }
    const charted = latestSnapshotPerAsOfDate(snapshots);
    const points = charted.map((snapshot) => ({
      x: asOfTimestamp(snapshot.asOfDate),
      y: snapshot.netCents / 100,
    }));
    const xs = points.map((point) => point.x);
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const pad = Math.max((maxX - minX) * 0.03, 15 * 24 * 60 * 60 * 1000);
    netWorthChartInstance = new Chart(canvas, {
      type: 'line',
      data: {
        datasets: [{
          label: 'Net worth',
          data: points,
          borderColor: '#38bdf8',
          backgroundColor: 'rgba(56, 189, 248, 0.15)',
          fill: false,
          tension: 0,
          pointRadius: 4,
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
                const snapshot = charted[items[0]?.dataIndex];
                return snapshot ? snapshot.asOfDate : '';
              },
              label(context) {
                const snapshot = charted[context.dataIndex];
                const amount = AcruetNetWorth.formatCents(snapshot.netCents);
                const breakdown = netWorthBreakdownTitle(snapshot);
                return breakdown ? `${amount} · ${breakdown}` : amount;
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
              scale.ticks = yearTickValues(scale.min, scale.max).map((value) => ({ value }));
            },
            ticks: {
              color: '#94a3b8',
              callback(value) {
                return String(new Date(value).getUTCFullYear());
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

  function asOfTimestamp(asOfDate) {
    return Date.parse(`${asOfDate}T12:00:00Z`);
  }

  function yearTickValues(min, max) {
    const startYear = new Date(min).getUTCFullYear();
    const endYear = new Date(max).getUTCFullYear();
    const ticks = [];
    for (let year = startYear; year <= endYear; year += 1) {
      let timestamp = Date.parse(`${year}-01-01T12:00:00Z`);
      if (timestamp < min) {
        timestamp = min;
      }
      if (timestamp > max) {
        timestamp = max;
      }
      ticks.push(timestamp);
    }
    return ticks;
  }

  function showError(message) {
    if (!els.error) {
      return;
    }
    els.error.textContent = message;
    els.error.hidden = false;
  }

  function hideError() {
    if (els.error) {
      els.error.hidden = true;
      els.error.textContent = '';
    }
  }

  function showTxError(message) {
    refreshElementRefs();
    const target = els.txError || els.error || document.getElementById('txReportError')
      || document.getElementById('reportError');
    if (!target) {
      console.error('Transaction report error:', message);
      return;
    }
    target.textContent = message;
    target.hidden = false;
    target.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  function hideTxError() {
    if (els.txError) {
      els.txError.hidden = true;
      els.txError.textContent = '';
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      refreshElementRefs();
      wirePanelActions();
    });
  } else {
    refreshElementRefs();
    wirePanelActions();
  }

  return {
    init,
    showReportsEntry,
    closeAll,
    showTxReportClick,
    downloadTxCsvClick,
  };
})();
