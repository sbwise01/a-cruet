/**
 * Household member invite (Phase 12c).
 */
document.addEventListener('DOMContentLoaded', async () => {
  const inviteForm = document.getElementById('inviteForm');
  const emailInput = document.getElementById('inviteEmail');
  const sendBtn = document.getElementById('btnSendInvite');
  const errorEl = document.getElementById('inviteError');
  const successEl = document.getElementById('inviteSuccess');
  const lockedNotice = document.getElementById('inviteLockedNotice');

  if (!inviteForm || !emailInput || !sendBtn) {
    return;
  }

  await AcruetCrypto.session.ensureReady();
  updateLockedState();
  emailInput.focus();

  document.addEventListener('acruet:crypto-changed', updateLockedState);

  inviteForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    await sendInvite();
  });

  async function sendInvite() {
    hide(errorEl);
    hide(successEl);

    if (!AcruetCrypto.session.isUnlocked()) {
      show(errorEl, 'Unlock your encryption key before sending an invitation.');
      updateLockedState();
      return;
    }

    const email = emailInput.value.trim();
    if (!email) {
      show(errorEl, 'Email address is required.');
      return;
    }

    const dekKey = AcruetCrypto.session.getDek();
    if (!dekKey) {
      show(errorEl, 'Unlock your encryption key before sending an invitation.');
      updateLockedState();
      return;
    }

    sendBtn.disabled = true;
    try {
      const inviteToken = AcruetCrypto.newInviteToken();
      const inviteWrap = await AcruetCrypto.buildHouseholdInviteWrap(dekKey, inviteToken);
      const response = await fetch('/household/invite/api', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email,
          inviteToken,
          wrappedDek: inviteWrap.wrappedDek,
          wrapAlgorithm: inviteWrap.wrapAlgorithm,
          kdfAlgorithm: inviteWrap.kdfAlgorithm,
          kdfHash: inviteWrap.kdfHash,
          kdfSalt: inviteWrap.kdfSalt,
          kdfIterations: inviteWrap.kdfIterations,
        }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        show(errorEl, body.error || 'Unable to send invitation.');
        return;
      }
      show(successEl, body.message || 'Invitation sent.');
      emailInput.value = '';
      emailInput.focus();
    } catch (error) {
      console.error(error);
      show(errorEl, 'Unable to send invitation.');
    } finally {
      updateLockedState();
    }
  }

  function updateLockedState() {
    const unlocked = AcruetCrypto.session.isUnlocked();
    sendBtn.disabled = !unlocked;
    if (lockedNotice) {
      lockedNotice.hidden = unlocked;
    }
  }

  function show(element, message) {
    element.textContent = message;
    element.hidden = false;
  }

  function hide(element) {
    element.hidden = true;
    element.textContent = '';
  }
});
