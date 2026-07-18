/**
 * Client-side reports — CSV export and balance chart (Phase 10).
 */
window.AcruetReports = (() => {
  let api = null;
  let chartInstance = null;
  let lastTxReport = null;

  const els = {};

  function init(ledgerApi) {
    api = ledgerApi;
    els.panel = document.getElementById('reportsPanel');
    els.hub = document.getElementById('reportsHub');
    els.transactionsPanel = document.getElementById('reportTransactionsPanel');
    els.chartPanel = document.getElementById('reportChartPanel');
    els.error = document.getElementById('reportError');
    if (!els.panel) {
      return;
    }

    document.getElementById('btnReportsBack').addEventListener('click', closeAll);
    document.getElementById('btnTxReportBack').addEventListener('click', showHub);
    document.getElementById('btnChartReportBack').addEventListener('click', showHub);
    document.getElementById('btnDownloadTxCsv').addEventListener('click', downloadTransactionsCsv);
    document.getElementById('btnShowTxReport').addEventListener('click', showTransactionsReport);
    document.getElementById('btnRenderChart').addEventListener('click', renderBalanceChart);

    els.hub.querySelectorAll('.report-tile').forEach((tile) => {
      tile.addEventListener('click', () => openReport(tile.dataset.report));
    });

    setDefaultDates(document.getElementById('txReportFrom'), document.getElementById('txReportTo'));
    setDefaultDates(document.getElementById('chartReportFrom'), document.getElementById('chartReportTo'));
  }

  function setDefaultDates(fromInput, toInput) {
    const today = new Date();
    const start = new Date(today.getFullYear(), today.getMonth(), 1);
    toInput.value = formatIsoDate(today);
    fromInput.value = formatIsoDate(start);
  }

  function setChartLayoutActive(active) {
    document.querySelector('main.page')?.classList.toggle('page--report-chart', active);
  }

  function showHub() {
    hideError();
    setChartLayoutActive(false);
    els.hub.hidden = false;
    els.transactionsPanel.hidden = true;
    els.chartPanel.hidden = true;
    destroyChart();
  }

  function openReport(kind) {
    hideError();
    els.hub.hidden = true;
    if (kind === 'transactions') {
      populateAccountList('txReportAccounts');
      setChartLayoutActive(false);
      clearTxReport();
      els.transactionsPanel.hidden = false;
      els.chartPanel.hidden = true;
      destroyChart();
      return;
    }
    if (kind === 'chart') {
      populateAccountList('chartReportAccounts');
      els.chartPanel.hidden = false;
      els.transactionsPanel.hidden = true;
      setChartLayoutActive(true);
      destroyChart();
    }
  }

  function showReportsEntry() {
    if (!els.panel || !api) {
      return;
    }
    api.beforeReportsOpen();
    els.panel.hidden = false;
    showHub();
  }

  function closeAll() {
    if (!els.panel) {
      return;
    }
    hideError();
    setChartLayoutActive(false);
    els.panel.hidden = true;
    showHub();
    destroyChart();
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
    const response = await fetch(
      `/ledger/transactions?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    );
    if (!response.ok) {
      throw new Error('Failed to load transactions for report.');
    }
    const body = await response.json();
    return api.decryptTransactions(body.transactions);
  }

  function clearTxReport() {
    lastTxReport = null;
    const downloadBtn = document.getElementById('btnDownloadTxCsv');
    const results = document.getElementById('txReportResults');
    downloadBtn.disabled = true;
    results.hidden = true;
    results.innerHTML = '';
  }

  function readTxReportInputs() {
    const accounts = api.getAccounts();
    if (accounts.length === 0) {
      throw new Error('Create at least one envelope first.');
    }
    const selectedIds = new Set(selectedAccountIds('txReportAccounts'));
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
    const accountNames = new Map(accounts.map((account) => [account.id, account.name]));
    const transactions = await loadTransactions(from, to);
    const rows = [['Date', 'Type', 'Memo', 'Envelope', 'Amount']];
    transactions
      .slice()
      .sort(compareTransactions)
      .forEach((transaction) => {
        transaction.payload.lines.forEach((line) => {
          if (!selectedIds.has(line.accountId)) {
            return;
          }
          rows.push([
            transaction.transactionDate,
            transaction.transactionType,
            transaction.payload.memo || '',
            accountNames.get(line.accountId) || '',
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
    hideError();
    const showBtn = document.getElementById('btnShowTxReport');
    showBtn.disabled = true;
    showBtn.textContent = 'Loading…';
    try {
      const { accounts, selectedIds, from, to } = readTxReportInputs();
      const rows = await buildTransactionRows(accounts, selectedIds, from, to);
      lastTxReport = { rows, from, to };
      renderTxReportTable(rows);
      document.getElementById('btnDownloadTxCsv').disabled = false;
    } catch (error) {
      clearTxReport();
      showError(error.message || 'Failed to load report.');
      console.error(error);
    } finally {
      showBtn.disabled = false;
      showBtn.textContent = 'Show report';
    }
  }

  function renderTxReportTable(rows) {
    const header = rows[0];
    const bodyRows = rows.slice(1);
    const results = document.getElementById('txReportResults');
    const tableRows = bodyRows
      .map((row) => {
        const amountClass = row[4] < 0 ? ' class="report-amount-negative"' : '';
        const amount = formatCents(row[4]);
        return `<tr>
          <td>${api.escapeHtml(row[0])}</td>
          <td>${api.escapeHtml(row[1])}</td>
          <td>${api.escapeHtml(row[2])}</td>
          <td>${api.escapeHtml(row[3])}</td>
          <td${amountClass}>${api.escapeHtml(amount)}</td>
        </tr>`;
      })
      .join('');
    results.innerHTML = `
      <table>
        <thead>
          <tr>${header.map((cell) => `<th>${api.escapeHtml(cell)}</th>`).join('')}</tr>
        </thead>
        <tbody>${tableRows}</tbody>
      </table>`;
    results.hidden = false;
  }

  function downloadTransactionsCsv() {
    hideError();
    try {
      if (!lastTxReport) {
        throw new Error('Show the report first.');
      }
      const csvRows = lastTxReport.rows.map((row, index) => {
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
      downloadCsv(
        `acruet-transactions-${lastTxReport.from}-to-${lastTxReport.to}.csv`,
        csvRows,
      );
    } catch (error) {
      showError(error.message || 'Failed to download CSV.');
      console.error(error);
    }
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
    return a.createdAt.localeCompare(b.createdAt);
  }

  function listDates(from, to) {
    const dates = [];
    const cursor = new Date(`${from}T12:00:00`);
    const end = new Date(`${to}T12:00:00`);
    while (cursor <= end) {
      dates.push(formatIsoDate(cursor));
      cursor.setDate(cursor.getDate() + 1);
    }
    return dates;
  }

  function formatIsoDate(date) {
    return date.toISOString().slice(0, 10);
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

  function showError(message) {
    els.error.textContent = message;
    els.error.hidden = false;
  }

  function hideError() {
    if (els.error) {
      els.error.hidden = true;
      els.error.textContent = '';
    }
  }

  return { init, showReportsEntry, closeAll };
})();
