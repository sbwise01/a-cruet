/**
 * Authenticated user avatar menu (Phase 9 item 4).
 */
document.addEventListener('DOMContentLoaded', async () => {
  const nav = document.getElementById('userNav');
  if (!nav) {
    return;
  }

  const avatarBtn = document.getElementById('avatarBtn');
  const menu = document.getElementById('avatarMenu');
  const accountLinked = nav.dataset.accountLinked === 'true';
  const keySetup = nav.dataset.keySetup === 'true';
  const inlineUnlockHome = nav.dataset.inlineUnlock === 'true';
  const displayName = nav.dataset.name || '';
  const email = nav.dataset.email || '';

  await AcruetCrypto.session.ensureReady();
  renderMenu();
  document.addEventListener('acruet:crypto-changed', renderMenu);

  avatarBtn.addEventListener('click', () => {
    const open = menu.hidden;
    menu.hidden = !open;
    avatarBtn.setAttribute('aria-expanded', String(open));
  });

  document.addEventListener('click', (event) => {
    if (!nav.contains(event.target)) {
      menu.hidden = true;
      avatarBtn.setAttribute('aria-expanded', 'false');
    }
  });

  function renderMenu() {
    const unlocked = AcruetCrypto.session.isUnlocked();
    let statusText = '';
    let actionsHtml = '';

    if (!accountLinked) {
      statusText = 'No a-cruet account linked.';
    } else if (!keySetup) {
      statusText = 'Encryption key not set up.';
      actionsHtml = '<a href="/keys/setup">Set up encryption key</a>';
    } else if (unlocked) {
      statusText = 'Encryption key unlocked for this session.';
      actionsHtml = `
        <button type="button" id="avatarLockKey">Lock key</button>
        <a href="/keys/rotate">Rotate key</a>`;
    } else {
      statusText = 'Encryption key locked.';
      if (inlineUnlockHome) {
        actionsHtml = '<button type="button" id="avatarUnlockKey">Unlock key</button>';
      } else {
        actionsHtml = '<a href="/keys/unlock?next=/">Unlock key</a>';
      }
      actionsHtml += '<a href="/keys/rotate">Rotate key</a>';
    }

    menu.innerHTML = `
      <div class="avatar-menu-identity">
        <div class="avatar-menu-name">${escapeHtml(displayName)}</div>
        ${email ? `<div class="avatar-menu-email hint">${escapeHtml(email)}</div>` : ''}
      </div>
      <div class="avatar-menu-status hint">${escapeHtml(statusText)}</div>
      <div class="avatar-menu-actions">${actionsHtml}</div>
      <a href="/auth/logout" class="avatar-menu-signout" id="avatarSignOut">Sign out</a>`;

    const signOut = document.getElementById('avatarSignOut');
    signOut.addEventListener('click', () => {
      AcruetCrypto.session.lock();
    });

    const lockBtn = document.getElementById('avatarLockKey');
    if (lockBtn) {
      lockBtn.addEventListener('click', () => {
        AcruetCrypto.session.lock();
        menu.hidden = true;
        avatarBtn.setAttribute('aria-expanded', 'false');
        if (window.AcruetLedger && window.AcruetLedger.onKeyLocked) {
          window.AcruetLedger.onKeyLocked();
        }
        renderMenu();
      });
    }

    const unlockBtn = document.getElementById('avatarUnlockKey');
    if (unlockBtn) {
      unlockBtn.addEventListener('click', () => {
        menu.hidden = true;
        avatarBtn.setAttribute('aria-expanded', 'false');
        if (window.AcruetLedger && window.AcruetLedger.showInlineUnlock) {
          window.AcruetLedger.showInlineUnlock();
        }
      });
    }
  }

  function escapeHtml(value) {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
});
