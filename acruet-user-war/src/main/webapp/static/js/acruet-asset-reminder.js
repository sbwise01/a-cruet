/**
 * Quarterly assets/liabilities reminder on the signed-in home launcher (PRODUCT.md §10).
 */
document.addEventListener('DOMContentLoaded', () => {
  const nav = document.getElementById('userNav');
  if (!nav
      || nav.dataset.accountLinked !== 'true'
      || nav.dataset.keySetup !== 'true'
      || window.location.pathname !== '/') {
    return;
  }

  const forced = new URLSearchParams(window.location.search).get('assetReminder') === '1';
  const today = localToday();
  const quarter = quarterId(today);
  if (!forced && (!quarter || !inLastSevenDaysOfMonth(today))) {
    return;
  }

  const storageKey = storageKeyFor(nav.dataset.email);
  const state = readState(storageKey);
  if (!forced && state.doneQuarter === quarter) {
    return;
  }
  if (!forced && state.notNowDate === isoDate(today)) {
    return;
  }

  const overlay = buildDialog();
  document.body.appendChild(overlay);

  const notNow = document.getElementById('assetReminderNotNow');
  const done = document.getElementById('assetReminderDone');
  done.focus();

  notNow.addEventListener('click', () => {
    writeState(storageKey, { ...state, notNowDate: isoDate(today) });
    overlay.remove();
  });
  done.addEventListener('click', () => {
    writeState(storageKey, { ...state, doneQuarter: quarter, notNowDate: '' });
    overlay.remove();
  });
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

  function buildDialog() {
    const root = document.createElement('div');
    root.id = 'assetReminderOverlay';
    root.className = 'asset-reminder-overlay';
    root.setAttribute('role', 'dialog');
    root.setAttribute('aria-modal', 'true');
    root.setAttribute('aria-labelledby', 'assetReminderTitle');
    root.innerHTML = `
      <div class="asset-reminder-dialog">
        <h2 id="assetReminderTitle">Time to update assets</h2>
        <p>The quarter is ending. Review snapshot values for cars, investments, loans, and similar items. Envelope totals update on their own.</p>
        <p><a href="/assets">Open the Assets page</a></p>
        <p class="asset-reminder-actions">
          <button type="button" id="assetReminderNotNow" class="secondary">Not now</button>
          <button type="button" id="assetReminderDone">Done</button>
        </p>
      </div>`;
    return root;
  }

  function localToday() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }

  function isoDate(date) {
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
  }

  function quarterId(date) {
    switch (date.getMonth()) {
      case 2:
        return `${date.getFullYear()}-Q1`;
      case 5:
        return `${date.getFullYear()}-Q2`;
      case 8:
        return `${date.getFullYear()}-Q3`;
      case 11:
        return `${date.getFullYear()}-Q4`;
      default:
        return null;
    }
  }

  function inLastSevenDaysOfMonth(date) {
    const last = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
    const day = date.getDate();
    return day >= last - 6 && day <= last;
  }

  function storageKeyFor(email) {
    return `acruet.assetReminder.v1:${email || 'user'}`;
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
      notNowDate: next.notNowDate || '',
      doneQuarter: next.doneQuarter || '',
    }));
  }
});
