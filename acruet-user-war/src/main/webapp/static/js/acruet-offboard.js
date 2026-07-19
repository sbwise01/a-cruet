/**
 * Offboard export — client-side decrypted JSON/CSV bundle (Phase 11).
 */
document.addEventListener('DOMContentLoaded', async () => {
  const lockedPanel = document.getElementById('offboardLocked');
  const exportPanel = document.getElementById('offboardExport');
  if (!lockedPanel || !exportPanel) {
    return;
  }

  const els = {
    passphrase: document.getElementById('offboardPassphrase'),
    unlockError: document.getElementById('offboardUnlockError'),
    exportError: document.getElementById('offboardExportError'),
    completeSuccess: document.getElementById('offboardCompleteSuccess'),
  };

  await AcruetCrypto.session.ensureReady();
  if (AcruetCrypto.session.isUnlocked()) {
    showExportPanel();
  }

  document.getElementById('btnOffboardUnlock').addEventListener('click', unlock);
  if (els.passphrase) {
    els.passphrase.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        unlock();
      }
    });
  }
  document.getElementById('btnDownloadJson').addEventListener('click', () => void downloadJson());
  document.getElementById('btnDownloadCsv').addEventListener('click', () => void downloadCsv());
  document.getElementById('btnMarkComplete').addEventListener('click', () => void markComplete());

  async function unlock() {
    hideError(els.unlockError);
    try {
      await AcruetCrypto.session.unlock(els.passphrase.value);
      showExportPanel();
    } catch (error) {
      showError(els.unlockError, error.message || 'Unlock failed.');
    }
  }

  function showExportPanel() {
    lockedPanel.hidden = true;
    exportPanel.hidden = false;
  }

  async function loadLedgerData() {
    const [accountsResponse, transactionsResponse] = await Promise.all([
      fetch('/ledger/accounts?includeArchived=true'),
      fetch('/ledger/transactions'),
    ]);
    if (!accountsResponse.ok || !transactionsResponse.ok) {
      throw new Error('Failed to load ledger data for export.');
    }
    const accountsBody = await accountsResponse.json();
    const transactionsBody = await transactionsResponse.json();
    const accounts = await decryptAccounts(accountsBody.accounts || []);
    const transactions = await decryptTransactions(transactionsBody.transactions || []);
    return { accounts, transactions };
  }

  async function decryptAccounts(accounts) {
    const dek = AcruetCrypto.session.getDek();
    const decrypted = [];
    for (const account of accounts) {
      const payload = await AcruetCrypto.decryptJson(dek, account.encryptedName);
      decrypted.push({
        id: account.id,
        status: account.status,
        name: payload.name,
        createdAt: account.createdAt,
        archivedAt: account.archivedAt,
      });
    }
    return decrypted;
  }

  async function decryptTransactions(transactions) {
    const dek = AcruetCrypto.session.getDek();
    const decrypted = [];
    for (const transaction of transactions) {
      const payload = await AcruetCrypto.decryptJson(dek, transaction.encryptedPayload);
      decrypted.push({
        id: transaction.id,
        transactionType: transaction.transactionType,
        transactionDate: transaction.transactionDate,
        createdAt: transaction.createdAt,
        memo: payload.memo || '',
        lines: Array.isArray(payload.lines) ? payload.lines : [],
      });
    }
    return decrypted;
  }

  async function downloadJson() {
    hideError(els.exportError);
    try {
      requireUnlocked();
      const data = await loadLedgerData();
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: 'application/json;charset=utf-8',
      });
      triggerDownload(blob, `acruet-export-${isoDate(new Date())}.json`);
    } catch (error) {
      showError(els.exportError, error.message || 'JSON export failed.');
    }
  }

  async function downloadCsv() {
    hideError(els.exportError);
    try {
      requireUnlocked();
      const data = await loadLedgerData();
      const accountNames = new Map(data.accounts.map((account) => [account.id, account.name]));
      const rows = [['Date', 'Type', 'Memo', 'Envelope', 'Amount']];
      data.transactions
        .slice()
        .sort((left, right) => String(left.transactionDate).localeCompare(String(right.transactionDate)))
        .forEach((transaction) => {
          transaction.lines.forEach((line) => {
            rows.push([
              transaction.transactionDate,
              transaction.transactionType,
              transaction.memo,
              accountNames.get(line.accountId) || '',
              formatCents(line.amountCents),
            ]);
          });
        });
      const csv = rows.map((row) => row.map(escapeCsvCell).join(',')).join('\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      triggerDownload(blob, `acruet-transactions-${isoDate(new Date())}.csv`);
    } catch (error) {
      showError(els.exportError, error.message || 'CSV export failed.');
    }
  }

  async function markComplete() {
    hideError(els.exportError);
    try {
      const response = await fetch('/offboard/api/complete', { method: 'POST' });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || 'Could not mark export complete.');
      }
      els.completeSuccess.hidden = false;
      els.completeSuccess.textContent =
        'Export marked complete. Your a-cruet data will be removed automatically.';
      document.getElementById('btnMarkComplete').disabled = true;
    } catch (error) {
      showError(els.exportError, error.message || 'Could not mark export complete.');
    }
  }

  function requireUnlocked() {
    if (!AcruetCrypto.session.isUnlocked()) {
      throw new Error('Unlock your ledger before exporting.');
    }
  }

  function triggerDownload(blob, filename) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  function formatCents(cents) {
    const value = Number(cents || 0) / 100;
    const sign = value < 0 ? '-' : '';
    return `${sign}$${Math.abs(value).toFixed(2)}`;
  }

  function escapeCsvCell(value) {
    const text = value == null ? '' : String(value);
    if (/[",\n]/.test(text)) {
      return `"${text.replace(/"/g, '""')}"`;
    }
    return text;
  }

  function isoDate(date) {
    return date.toISOString().slice(0, 10);
  }

  function showError(element, message) {
    if (!element) {
      return;
    }
    element.textContent = message;
    element.hidden = false;
  }

  function hideError(element) {
    if (element) {
      element.hidden = true;
      element.textContent = '';
    }
  }
});
