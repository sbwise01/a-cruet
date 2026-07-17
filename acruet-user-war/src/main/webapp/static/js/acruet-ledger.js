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

  const ALLOCATION_MISMATCH_MESSAGE =
    'Your total amount must match the sum of all of the allocated amounts.';

  const els = {
    error: document.getElementById('ledgerError'),
    warning: document.getElementById('ledgerWarning'),
    formError: document.getElementById('formError'),
    accountsList: document.getElementById('accountsList'),
    accountLimitHint: document.getElementById('accountLimitHint'),
    formPanel: document.getElementById('formPanel'),
    formTitle: document.getElementById('formTitle'),
    formBody: document.getElementById('formBody'),
  };

  let formErrorTimeout = null;

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
    const rows = state.accounts
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
    const grandTotal = state.accounts.reduce(
      (sum, account) => sum + (state.balances.get(account.id) || 0),
      0,
    );
    const grandTotalClass = grandTotal < 0 ? ' balance-negative' : '';
    els.accountsList.innerHTML = `
      ${rows}
      <div class="account-row total-row">
        <span class="name">Total</span>
        <span class="balance${grandTotalClass}">${formatCents(grandTotal)}</span>
      </div>`;
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
    hideFormError();
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
      addDepositLines();
      document.getElementById('txTotal').addEventListener('input', autoFillDepositFromTotal);
    } else if (type === 'WITHDRAW') {
      addWithdrawLines();
      document.getElementById('txTotal').addEventListener('input', autoFillWithdrawFromTotal);
    } else {
      addTransferLines();
      document.getElementById('txTotal').addEventListener('input', autoFillTransferFromTotal);
    }
    hideFormError();
    els.formPanel.hidden = false;
  }

  function addDepositLines() {
    const container = document.getElementById('lineContainer');
    container.innerHTML = `
      <label>Allocate to envelopes (amounts must equal total)</label>
      <div id="depositLines"></div>
      <button type="button" id="btnAddDepositLine">Add envelope row</button>
    `;
    document.getElementById('btnAddDepositLine').addEventListener('click', () => appendDepositLine());
    appendDepositLine();
    updateAllocationHint();
  }

  function appendDepositLine() {
    const lines = document.getElementById('depositLines');
    const row = document.createElement('div');
    row.className = 'line-row';
    row.innerHTML = `
      ${accountSelect('depositAccount')}
      <input type="number" class="depositAmount" min="0" step="0.01" value="0.00">
    `;
    row.querySelector('.depositAmount').addEventListener('input', syncDepositLines);
    lines.appendChild(row);
  }

  function addWithdrawLines() {
    const container = document.getElementById('lineContainer');
    container.innerHTML = `
      <label>Withdraw from envelopes (amounts must equal total)</label>
      <div id="withdrawLines"></div>
      <button type="button" id="btnAddWithdrawLine">Add envelope row</button>
    `;
    document.getElementById('btnAddWithdrawLine').addEventListener('click', () => appendWithdrawLine());
    appendWithdrawLine();
    updateWithdrawHint();
  }

  function appendWithdrawLine() {
    const lines = document.getElementById('withdrawLines');
    const row = document.createElement('div');
    row.className = 'line-row';
    row.innerHTML = `
      ${accountSelect('withdrawAccount')}
      <input type="number" class="withdrawAmount" min="0" step="0.01" value="0.00">
    `;
    row.querySelector('.withdrawAmount').addEventListener('input', syncWithdrawLines);
    lines.appendChild(row);
  }

  function syncWithdrawLines() {
    updateWithdrawHint();
  }

  function addTransferLines() {
    const container = document.getElementById('lineContainer');
    container.innerHTML = `
      <label>From</label>
      <div class="line-row">${accountSelect('transferFrom')}</div>
      <label>To envelopes (amounts must equal total)</label>
      <div id="transferTos"></div>
      <button type="button" id="btnAddTransferTo">Add destination</button>
    `;
    document.getElementById('btnAddTransferTo').addEventListener('click', () => appendTransferTo());
    appendTransferTo();
    updateTransferHint();
  }

  function appendTransferTo() {
    const row = document.createElement('div');
    row.className = 'line-row';
    row.innerHTML = `
      ${accountSelect('transferTo')}
      <input type="number" class="transferAmount" min="0" step="0.01" value="0.00">
    `;
    row.querySelector('.transferAmount').addEventListener('input', syncTransferLines);
    document.getElementById('transferTos').appendChild(row);
    updateTransferHint();
  }

  function syncTransferLines() {
    updateTransferHint();
  }

  function autoFillTransferFromTotal() {
    const totalInput = document.getElementById('txTotal');
    if (!totalInput) {
      return;
    }
    const amountInputs = Array.from(document.querySelectorAll('.transferAmount'));
    if (amountInputs.length === 1) {
      const totalValue = totalInput.value.trim();
      amountInputs[0].value = totalValue === '' ? '0.00' : formatUsdInput(parseUsd(totalValue));
    }
    updateTransferHint();
  }

  function updateTransferHint() {
    const hint = document.getElementById('allocationHint');
    const totalInput = document.getElementById('txTotal');
    if (!hint || !totalInput) {
      return;
    }
    const total = parseUsd(totalInput.value);
    const allocated = sumTransferLines();
    hint.textContent = `Allocated: ${formatCents(allocated)} of ${formatCents(total)}`;
  }

  function sumTransferLines() {
    return Array.from(document.querySelectorAll('.transferAmount'))
      .reduce((sum, input) => sum + parseUsd(input.value), 0);
  }

  function accountSelect(className) {
    const options = state.accounts
      .map((account) => `<option value="${account.id}">${escapeHtml(account.name)}</option>`)
      .join('');
    return `<select class="${className}" required>${options}</select>`;
  }

  function syncDepositLines() {
    updateAllocationHint();
  }

  function autoFillDepositFromTotal() {
    const totalInput = document.getElementById('txTotal');
    if (!totalInput) {
      return;
    }
    const amountInputs = Array.from(document.querySelectorAll('.depositAmount'));
    if (amountInputs.length === 1) {
      const totalValue = totalInput.value.trim();
      amountInputs[0].value = totalValue === '' ? '0.00' : formatUsdInput(parseUsd(totalValue));
    }
    updateAllocationHint();
  }

  function autoFillWithdrawFromTotal() {
    const totalInput = document.getElementById('txTotal');
    if (!totalInput) {
      return;
    }
    const amountInputs = Array.from(document.querySelectorAll('.withdrawAmount'));
    if (amountInputs.length === 1) {
      const totalValue = totalInput.value.trim();
      amountInputs[0].value = totalValue === '' ? '0.00' : formatUsdInput(parseUsd(totalValue));
    }
    updateWithdrawHint();
  }

  function updateWithdrawHint() {
    const hint = document.getElementById('allocationHint');
    const totalInput = document.getElementById('txTotal');
    if (!hint || !totalInput) {
      return;
    }
    const total = parseUsd(totalInput.value);
    const withdrawn = sumWithdrawLines();
    hint.textContent = `Withdrawn: ${formatCents(withdrawn)} of ${formatCents(total)}`;
  }

  function sumWithdrawLines() {
    return Array.from(document.querySelectorAll('.withdrawAmount'))
      .reduce((sum, input) => sum + parseUsd(input.value), 0);
  }

  function updateAllocationHint() {
    const hint = document.getElementById('allocationHint');
    const totalInput = document.getElementById('txTotal');
    if (!hint || !totalInput) {
      return;
    }
    const total = parseUsd(totalInput.value);
    const allocated = sumDepositLines();
    hint.textContent = `Allocated: ${formatCents(allocated)} of ${formatCents(total)}`;
  }

  function sumDepositLines() {
    return Array.from(document.querySelectorAll('.depositAmount'))
      .reduce((sum, input) => sum + parseUsd(input.value), 0);
  }

  function parseUsd(value) {
    if (value === null || value === undefined || String(value).trim() === '') {
      return 0;
    }
    const dollars = Number.parseFloat(value);
    if (Number.isNaN(dollars)) {
      return 0;
    }
    return Math.round(dollars * 100);
  }

  async function submitForm() {
    hideError();
    hideWarning();
    hideFormError();
    try {
      if (state.formMode === 'CREATE') {
        await submitCreate();
      } else {
        const saved = await submitTransaction();
        if (!saved) {
          return;
        }
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
      if (totalCents <= 0) {
        throw new Error('Enter a deposit total greater than zero.');
      }
      lines = Array.from(document.querySelectorAll('#depositLines .line-row'))
        .map((row) => ({
          accountId: row.querySelector('select').value,
          amountCents: parseUsd(row.querySelector('.depositAmount').value),
        }))
        .filter((line) => line.amountCents > 0);
      if (lines.length === 0) {
        throw new Error('Enter at least one allocation amount greater than zero.');
      }
      const allocated = lines.reduce((sum, line) => sum + line.amountCents, 0);
      if (allocated !== totalCents) {
        if (document.querySelectorAll('#depositLines .line-row').length > 1) {
          showFormError(ALLOCATION_MISMATCH_MESSAGE);
          return false;
        }
        throw new Error(
          `Allocations (${formatCents(allocated)}) must equal the deposit total (${formatCents(totalCents)}).`,
        );
      }
    } else if (type === 'WITHDRAW') {
      totalCents = parseUsd(document.getElementById('txTotal').value);
      if (totalCents <= 0) {
        throw new Error('Enter a withdraw total greater than zero.');
      }
      const positiveLines = Array.from(document.querySelectorAll('#withdrawLines .line-row'))
        .map((row) => ({
          accountId: row.querySelector('select').value,
          amountCents: parseUsd(row.querySelector('.withdrawAmount').value),
        }))
        .filter((line) => line.amountCents > 0);
      if (positiveLines.length === 0) {
        throw new Error('Enter at least one withdrawal amount greater than zero.');
      }
      const withdrawn = positiveLines.reduce((sum, line) => sum + line.amountCents, 0);
      if (withdrawn !== totalCents) {
        if (document.querySelectorAll('#withdrawLines .line-row').length > 1) {
          showFormError(ALLOCATION_MISMATCH_MESSAGE);
          return false;
        }
        throw new Error(
          `Withdrawals (${formatCents(withdrawn)}) must equal the total (${formatCents(totalCents)}).`,
        );
      }
      lines = positiveLines.map((line) => ({
        accountId: line.accountId,
        amountCents: -line.amountCents,
      }));
    } else {
      totalCents = parseUsd(document.getElementById('txTotal').value);
      if (totalCents <= 0) {
        throw new Error('Enter a transfer total greater than zero.');
      }
      const fromId = document.querySelector('.transferFrom').value;
      const positiveLines = Array.from(document.querySelectorAll('#transferTos .line-row'))
        .map((row) => ({
          accountId: row.querySelector('select').value,
          amountCents: parseUsd(row.querySelector('.transferAmount').value),
        }))
        .filter((line) => line.amountCents > 0);
      if (positiveLines.length === 0) {
        throw new Error('Enter at least one destination amount greater than zero.');
      }
      const allocated = positiveLines.reduce((sum, line) => sum + line.amountCents, 0);
      if (allocated !== totalCents) {
        if (document.querySelectorAll('#transferTos .line-row').length > 1) {
          showFormError(ALLOCATION_MISMATCH_MESSAGE);
          return false;
        }
        throw new Error(
          `Allocations (${formatCents(allocated)}) must equal the transfer total (${formatCents(totalCents)}).`,
        );
      }
      lines = [{ accountId: fromId, amountCents: -totalCents }];
      positiveLines.forEach((line) => {
        lines.push({
          accountId: line.accountId,
          amountCents: line.amountCents,
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
    return true;
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
    hideFormError();
    els.formPanel.hidden = true;
    state.formMode = null;
    els.formBody.innerHTML = '';
  }

  function showFormError(message, durationMs = 5000) {
    if (!els.formError) {
      return;
    }
    if (formErrorTimeout) {
      clearTimeout(formErrorTimeout);
    }
    els.formError.textContent = message;
    els.formError.hidden = false;
    formErrorTimeout = setTimeout(() => {
      hideFormError();
    }, durationMs);
  }

  function hideFormError() {
    if (formErrorTimeout) {
      clearTimeout(formErrorTimeout);
      formErrorTimeout = null;
    }
    if (els.formError) {
      els.formError.hidden = true;
      els.formError.textContent = '';
    }
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

  function formatCents(cents) {
    const sign = cents < 0 ? '-' : '';
    const abs = Math.abs(cents);
    return `${sign}$${(abs / 100).toFixed(2)}`;
  }

  function formatUsdInput(cents) {
    return (cents / 100).toFixed(2);
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
