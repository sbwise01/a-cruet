/**
 * Home net-worth reminder when the register is empty or a month stale (PRODUCT.md §11).
 */
document.addEventListener('DOMContentLoaded', async () => {
  const nav = document.getElementById('userNav');
  if (!nav
      || nav.dataset.accountLinked !== 'true'
      || nav.dataset.keySetup !== 'true'
      || window.location.pathname !== '/') {
    return;
  }

  const forced = new URLSearchParams(window.location.search).get('netWorthReminder') === '1';
  if (typeof AcruetNetWorth === 'undefined') {
    return;
  }

  await AcruetCrypto.session.ensureReady();
  await whenAssetReminderClosed();
  if (!forced && !(await shouldShow())) {
    return;
  }

  const latest = await loadLatest().catch(() => ({ snapshot: null }));
  const firstSave = !latest.snapshot;
  const overlay = buildDialog(firstSave, latest.snapshot && latest.snapshot.asOfDate);
  document.body.appendChild(overlay);

  const notNow = document.getElementById('netWorthReminderNotNow');
  const save = document.getElementById('netWorthReminderSave');
  const error = document.getElementById('netWorthReminderError');
  save.focus();

  const storageKey = storageKeyFor(nav.dataset.email);
  notNow.addEventListener('click', () => {
    writeState(storageKey, { notNowUntil: AcruetNetWorth.snoozeUntil() });
    overlay.remove();
  });
  save.addEventListener('click', () => void saveCurrent(overlay, save, error));
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay) {
      notNow.click();
    }
  });
  document.addEventListener('keydown', function onKey(event) {
    if (event.key === 'Escape' && document.body.contains(overlay)) {
      event.preventDefault();
      notNow.click();
      document.removeEventListener('keydown', onKey);
    }
  });

  async function shouldShow() {
    const state = readState(storageKeyFor(nav.dataset.email));
    if (AcruetNetWorth.isSnoozed(state.notNowUntil)) {
      return false;
    }
    try {
      const body = await loadLatest();
      return AcruetNetWorth.isStale(body.snapshot && body.snapshot.asOfDate);
    } catch (ignored) {
      return false;
    }
  }

  async function loadLatest() {
    return AcruetNetWorth.latest();
  }

  async function saveCurrent(dialog, button, errorEl) {
    errorEl.hidden = true;
    if (!AcruetCrypto.session.isUnlocked()) {
      window.location.href = '/keys/unlock?next=' + encodeURIComponent('/?netWorthReminder=1');
      return;
    }
    button.disabled = true;
    button.textContent = 'Saving…';
    try {
      const live = await AcruetNetWorth.computeLiveNet();
      await AcruetNetWorth.create(
        AcruetNetWorth.localIsoDate(),
        AcruetNetWorth.computedPayload(live),
      );
      writeState(storageKey, { notNowUntil: '' });
      dialog.remove();
    } catch (failure) {
      errorEl.textContent = failure.message || 'Failed to save net worth.';
      errorEl.hidden = false;
      button.disabled = false;
      button.textContent = 'Save net worth';
    }
  }

  function buildDialog(empty, asOfDate) {
    const root = document.createElement('div');
    root.id = 'netWorthReminderOverlay';
    root.className = 'asset-reminder-overlay';
    root.setAttribute('role', 'dialog');
    root.setAttribute('aria-modal', 'true');
    root.setAttribute('aria-labelledby', 'netWorthReminderTitle');
    const title = empty ? 'Save your first net worth' : 'Net worth is over a month old';
    const body = empty
      ? 'Start a history of household net worth. Save today’s calculated total (assets minus liabilities), or add past year-end totals from Assets.'
      : `Last saved as of ${asOfDate}. Save today’s calculated net to keep the history current.`;
    root.innerHTML = `
      <div class="asset-reminder-dialog">
        <h2 id="netWorthReminderTitle">${title}</h2>
        <p>${body}</p>
        <p><a href="/assets">Update line items on Assets</a></p>
        <p id="netWorthReminderError" class="error" hidden></p>
        <p class="asset-reminder-actions">
          <button type="button" id="netWorthReminderNotNow" class="secondary">Not now</button>
          <button type="button" id="netWorthReminderSave">Save net worth</button>
        </p>
      </div>`;
    return root;
  }

  function whenAssetReminderClosed() {
    return new Promise((resolve) => {
      if (window.__acruetAssetReminderSettled && !document.getElementById('assetReminderOverlay')) {
        resolve();
        return;
      }
      document.addEventListener('acruet:asset-reminder-settled', () => resolve(), { once: true });
      const observer = new MutationObserver(() => {
        if (!document.getElementById('assetReminderOverlay') && window.__acruetAssetReminderSettled) {
          observer.disconnect();
          resolve();
        }
      });
      observer.observe(document.body, { childList: true });
    });
  }

  function storageKeyFor(email) {
    return `acruet.netWorthReminder.v1:${email || 'user'}`;
  }

  function readState(key) {
    try {
      const raw = window.localStorage.getItem(key);
      if (!raw) {
        return {};
      }
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch (ignored) {
      return {};
    }
  }

  function writeState(key, next) {
    window.localStorage.setItem(key, JSON.stringify({
      notNowUntil: next.notNowUntil || '',
    }));
  }
});
