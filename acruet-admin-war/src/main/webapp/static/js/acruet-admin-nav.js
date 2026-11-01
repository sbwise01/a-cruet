/**
 * Authenticated admin avatar menu (Dashboard + Sign out).
 */
document.addEventListener('DOMContentLoaded', () => {
  const nav = document.getElementById('adminNav');
  if (!nav) {
    return;
  }

  const avatarBtn = document.getElementById('avatarBtn');
  const menu = document.getElementById('avatarMenu');
  const displayName = nav.dataset.name || '';
  const email = nav.dataset.email || '';

  renderMenu();

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
    menu.innerHTML = `
      <div class="avatar-menu-identity">
        <div class="avatar-menu-name">${escapeHtml(displayName)}</div>
        ${email ? `<div class="avatar-menu-email hint">${escapeHtml(email)}</div>` : ''}
      </div>
      <div class="avatar-menu-actions">
        <a href="/">Dashboard</a>
      </div>
      <a href="/auth/logout" class="avatar-menu-signout">Sign out</a>`;
  }

  function escapeHtml(value) {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
});
