/**
 * Ledger UI — client-side decrypt, balance computation, envelope operations (Phase 8).
 */
document.addEventListener('DOMContentLoaded', async () => {
  const state = {
    accounts: [],
    transactions: [],
    balances: new Map(),
    formMode: null,
  };

  const els = {
    error: document.getElementById('ledgerError'),
    warning: document.getElementById('ledgerWarning'),
    accountsList: document.getElementById('accountsList'),
    accountLimitHint: document.getElementById('accountLimitHint'),
    formPanel: document.getElementById('formPanel'),
    formTitle: document.getElementById('formTitle'),
    formBody: document.getElementById('formBody'),
  };

  await AcruetCrypto.session.ensureReady();
  if (!AcruetCrypto.session.isUnlocked()) {
    window.location.assign('/keys/unlock?next=/ledger');
    return;
  }

  document.getElementById('btnShowCreate').addEventListener('click', () => showCreateForm());
  document.getElementById('btnShowDeposit').addEventListener('click', () => showTransactionForm('DEPOSIT'));
  document.getElementById('btnShowWithdraw').addEventListener('click', () => showTransactionForm('WITHDRAW'));
  document.getElementById('btnShowTransfer').addEventListener('click', () => showTransactionForm('TRANSFER'));
  document.getElementById('btnCancelForm').addEventListener('click', hideForm);
  document.getElementById('btnSubmitForm').addEventListener('click', submitForm);

  await refresh();

  async function refresh() {
    hideError();
    hideWarning();
    const [accountsResponse, transactionsResponse] = await Promise.all([
      fetch('/ledger/accounts'),
      fetch('/ledger/transactions'),
    ]);
    if (!accountsResponse.ok || !transactionsResponse.ok) {
      showError('Failed to load ledger data.');
      return;
    }
    const accountsBody = await accountsResponse.json();
    const transactionsBody = await transactionsResponse.json();
    state.accounts = await decryptAccounts(accountsBody.accounts);
    state.transactions = await decryptTransactions(transactionsBody.transactions);
    state.balances = computeBalances(state.accounts, state.transactions);
    els.accountLimitHint.textContent =
      `${accountsBody.accountCount} of ${accountsBody.accountLimit} envelopes in use`;
    renderAccounts();
  }

  async function decryptAccounts(accounts) {
    const dek = AcruetCrypto.session.getDek();
    const decrypted = [];
    for (const account of accounts) {
      const payload = await AcruetCrypto.decryptJson(dek, account.encryptedName);
      decrypted.push({ ...account, name: payload.name });
    }
    return decrypted.filter((account) => account.status === 'ACTIVE');
  }

  async function decryptTransactions(transactions) {
    const dek = AcruetCrypto.session.getDek();
    const decrypted = [];
    for (const transaction of transactions) {
      const payload = await AcruetCrypto.decryptJson(dek, transaction.encryptedPayload);
      decrypted.push({ ...transaction, payload });
    }
    return decrypted;
  }

  function computeBalances(accounts, transactions) {
    const balances = new Map();
    accounts.forEach((account) => balances.set(account.id, 0));
    transactions.forEach((transaction) => {
      transaction.payload.lines.forEach((line) => {
        const current = balances.get(line.accountId) || 0;
        balances.set(line.accountId, current + line.amountCents);
      });
    });
    return balances;
  }

  function renderAccounts() {
    if (state.accounts.length === 0) {
      els.accountsList.innerHTML = '<p class="hint">No envelopes yet. Create one to get started.</p>';
      return;
    }
    els.accountsList.innerHTML = state.accounts
      .map((account) => {
        const balance = state.balances.get(account.id) || 0;
        const negativeClass = balance < 0 ? ' balance-negative' : '';
        return `
          <div class="account-row">
            <span class="name">${escapeHtml(account.name)}</span>
            <span>
              <span class="balance${negativeClass}">${formatCents(balance)}</span>
              ${balance === 0 ? `<button type="button" class="archive-btn secondary" data-id="${account.id}">Archive</button>` : ''}
            </span>
          </div>`;
      })
      .join('');
    els.accountsList.querySelectorAll('.archive-btn').forEach((button) => {
      button.addEventListener('click', async () => {
        try {
          await archiveAccount(button.dataset.id);
        } catch (error) {
          showError(error.message || 'Failed to archive envelope.');
        }
      });
    });
  }

  async function archiveAccount(accountId) {
    if (!window.confirm('Archive this envelope? It must have a zero balance.')) {
      return;
    }
    const response = await fetch(`/ledger/accounts/${accountId}/archive`, { method: 'POST' });
    if (!response.ok) {
      const body = await response.json();
      throw new Error(body.error || 'Failed to archive envelope.');
    }
    await refresh();
  }

  function showCreateForm() {
    state.formMode = 'CREATE';
    els.formTitle.textContent = 'New envelope';
    els.formBody.innerHTML = `
      <label for="accountName">Envelope name</label>
      <input id="accountName" type="text" required maxlength="120">
    `;
    els.formPanel.hidden = false;
  }

  function showTransactionForm(type) {
    if (state.accounts.length === 0) {
      showError('Create at least one envelope first.');
      return;
    }
    state.formMode = type;
    const titles = {
      DEPOSIT: 'Deposit funds',
      WITHDRAW: 'Withdraw funds',
      TRANSFER: 'Transfer funds',
    };
    els.formTitle.textContent = titles[type];
    const accountOptions = state.accounts
      .map((account) => `<option value="${account.id}">${escapeHtml(account.name)}</option>`)
      .join('');
    els.formBody.innerHTML = `
      <label for="txDate">Date</label>
      <input id="txDate" type="date" value="${todayIso()}" required>
      <label for="txMemo">Memo</label>
      <input id="txMemo" type="text" maxlength="200" placeholder="Optional">
      <label for="txTotal">Total amount (USD)</label>
      <input id="txTotal" type="number" min="0.01" step="0.01" required>
      <div id="lineContainer" class="form-grid"></div>
      <p class="hint" id="allocationHint"></p>
    `;
    if (type === 'DEPOSIT') {
      addDepositLine();
      document.getElementById('txTotal').addEventListener('input', syncDepositLines);
    } else if (type === 'WITHDRAW') {
      addWithdrawLine();
    } else {
      addTransferLines();
    }
    els.formPanel.hidden = false;
  }

  function addDepositLine() {
    const container = document.getElementById('lineContainer');
    container.innerHTML = `
      <label>Allocate to envelopes (must total 100%)</label>
      <div id="depositLines"></div>
      <button type="button" id="btnAddDepositLine">Add envelope</button>
    `;
    document.getElementById('btnAddDepositLine').addEventListener('click', () => appendDepositLine());
    appendDepositLine();
    appendDepositLine();
  }

  function appendDepositLine() {
    const lines = document.getElementById('depositLines');
    const row = document.createElement('div');
    row.className = 'line-row';
    row.innerHTML = `
      ${accountSelect('depositAccount')}
      <input type="number" class="depositAmount" min="0" step="0.01" placeholder="Amount">
    `;
    lines.appendChild(row);
  }

  function addWithdrawLine() {
    const container = document.getElementById('lineContainer');
    container.innerHTML = `
      <label>Withdraw from</label>
      <div class="line-row">
        ${accountSelect('withdrawAccount')}
        <input id="withdrawAmount" type="number" min="0.01" step="0.01" placeholder="Amount">
      </div>
    `;
  }

  function addTransferLines() {
    const container = document.getElementById('lineContainer');
    container.innerHTML = `
      <label>From</label>
      <div class="line-row">${accountSelect('transferFrom')}</div>
      <label>To</label>
      <div id="transferTos"></div>
      <button type="button" id="btnAddTransferTo">Add destination</button>
    `;
    document.getElementById('btnAddTransferTo').addEventListener('click', () => appendTransferTo());
    appendTransferTo();
  }

  function appendTransferTo() {
    const row = document.createElement('div');
    row.className = 'line-row';
    row.innerHTML = `
      ${accountSelect('transferTo')}
      <input type="number" class="transferAmount" min="0.01" step="0.01" placeholder="Amount">
    `;
    document.getElementById('transferTos').appendChild(row);
  }

  function accountSelect(className) {
    const options = state.accounts
      .map((account) => `<option value="${account.id}">${escapeHtml(account.name)}</option>`)
      .join('');
    return `<select class="${className}" required>${options}</select>`;
  }

  function syncDepositLines() {
    const hint = document.getElementById('allocationHint');
    if (!hint) return;
    const total = parseUsd(document.getElementById('txTotal').value);
    const allocated = sumDepositLines();
    hint.textContent = `Allocated: ${formatCents(allocated)} of ${formatCents(total)}`;
  }

  function sumDepositLines() {
    return Array.from(document.querySelectorAll('.depositAmount'))
      .reduce((sum, input) => sum + parseUsd(input.value), 0);
  }

  async function submitForm() {
    hideError();
    hideWarning();
    try {
      if (state.formMode === 'CREATE') {
        await submitCreate();
      } else {
        await submitTransaction();
      }
      hideForm();
      await refresh();
    } catch (error) {
      showError(error.message || 'Operation failed.');
      console.error(error);
    }
  }

  async function submitCreate() {
    const name = document.getElementById('accountName').value.trim();
    if (!name) throw new Error('Envelope name is required.');
    const dek = AcruetCrypto.session.getDek();
    const encryptedName = await AcruetCrypto.encryptJson(dek, { name });
    const response = await fetch('/ledger/accounts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ encryptedName }),
    });
    if (!response.ok) {
      const body = await response.json();
      throw new Error(body.error || 'Failed to create envelope.');
    }
  }

  async function submitTransaction() {
    const date = document.getElementById('txDate').value;
    const memo = document.getElementById('txMemo').value.trim();
    const type = state.formMode;
    let lines = [];
    let totalCents = 0;

    if (type === 'DEPOSIT') {
      totalCents = parseUsd(document.getElementById('txTotal').value);
      lines = Array.from(document.querySelectorAll('#depositLines .line-row')).map((row) => ({
        accountId: row.querySelector('select').value,
        amountCents: parseUsd(row.querySelector('.depositAmount').value),
      }));
      const allocated = lines.reduce((sum, line) => sum + line.amountCents, 0);
      if (allocated !== totalCents) {
        throw new Error('Deposit must allocate 100% of the total across envelopes.');
      }
      if (lines.some((line) => line.amountCents <= 0)) {
        throw new Error('Each allocation must be greater than zero.');
      }
    } else if (type === 'WITHDRAW') {
      const accountId = document.querySelector('.withdrawAccount').value;
      const amount = parseUsd(document.getElementById('withdrawAmount').value);
      totalCents = amount;
      lines = [{ accountId, amountCents: -amount }];
    } else {
      const fromId = document.querySelector('.transferFrom').value;
      const toLines = Array.from(document.querySelectorAll('#transferTos .line-row'));
      const transferTotal = toLines.reduce(
        (sum, row) => sum + parseUsd(row.querySelector('.transferAmount').value),
        0,
      );
      if (transferTotal <= 0) throw new Error('Transfer amount must be greater than zero.');
      totalCents = transferTotal;
      lines = [{ accountId: fromId, amountCents: -transferTotal }];
      toLines.forEach((row) => {
        lines.push({
          accountId: row.querySelector('select').value,
          amountCents: parseUsd(row.querySelector('.transferAmount').value),
        });
      });
    }

    const warnings = overspendWarnings(lines);
    if (warnings.length > 0) {
      showWarning(warnings.join(' '));
    }

    const accountIds = [...new Set(lines.map((line) => line.accountId))];
    const dek = AcruetCrypto.session.getDek();
    const encryptedPayload = await AcruetCrypto.encryptJson(dek, { memo, totalCents, lines });
    const response = await fetch('/ledger/transactions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        transactionType: type,
        transactionDate: date,
        encryptedPayload,
        accountIds,
      }),
    });
    if (!response.ok) {
      const body = await response.json();
      throw new Error(body.error || 'Failed to save transaction.');
    }
  }

  function overspendWarnings(lines) {
    const projected = new Map(state.balances);
    lines.forEach((line) => {
      projected.set(line.accountId, (projected.get(line.accountId) || 0) + line.amountCents);
    });
    const warnings = [];
    state.accounts.forEach((account) => {
      const balance = projected.get(account.id) || 0;
      if (balance < 0) {
        warnings.push(`"${account.name}" will be negative (${formatCents(balance)}).`);
      }
    });
    return warnings;
  }

  function hideForm() {
    els.formPanel.hidden = true;
    state.formMode = null;
    els.formBody.innerHTML = '';
  }

  function showError(message) {
    els.error.textContent = message;
    els.error.hidden = false;
  }

  function hideError() {
    els.error.hidden = true;
    els.error.textContent = '';
  }

  function showWarning(message) {
    els.warning.textContent = message;
    els.warning.hidden = false;
  }

  function hideWarning() {
    els.warning.hidden = true;
    els.warning.textContent = '';
  }

  function parseUsd(value) {
    const dollars = Number.parseFloat(value);
    if (Number.isNaN(dollars)) return 0;
    return Math.round(dollars * 100);
  }

  function formatCents(cents) {
    const sign = cents < 0 ? '-' : '';
    const abs = Math.abs(cents);
    return `${sign}$${(abs / 100).toFixed(2)}`;
  }

  function todayIso() {
    return new Date().toISOString().slice(0, 10);
  }

  function escapeHtml(value) {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
});
