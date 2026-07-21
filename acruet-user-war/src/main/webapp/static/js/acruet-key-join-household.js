document.addEventListener('DOMContentLoaded', () => {
  const inviteTokenInput = document.getElementById('inviteToken');
  const tokenError = document.getElementById('tokenError');
  const passphraseInput = document.getElementById('passphrase');
  const passphraseConfirmInput = document.getElementById('passphraseConfirm');
  const passphraseError = document.getElementById('passphraseError');
  const recoveryError = document.getElementById('recoveryError');
  const setupError = document.getElementById('setupError');
  const recoveryStatus = document.getElementById('recoveryStatus');
  const recoveryConfirmed = document.getElementById('recoveryConfirmed');
  const btnTokenNext = document.getElementById('btnTokenNext');
  const btnPassphraseNext = document.getElementById('btnPassphraseNext');
  const btnDownloadRecovery = document.getElementById('btnDownloadRecovery');
  const btnFinishSetup = document.getElementById('btnFinishSetup');
  const stepToken = document.getElementById('step-token');
  const stepPassphrase = document.getElementById('step-passphrase');
  const stepRecovery = document.getElementById('step-recovery');

  let inviteWrap = null;
  let setupState = null;

  function showError(element, message) {
    element.textContent = message;
    element.hidden = false;
  }

  function hideError(element) {
    element.hidden = true;
    element.textContent = '';
  }

  function bindPassphraseEnter(primary, confirm, onSubmit) {
    primary.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        confirm.focus();
      }
    });
    confirm.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        onSubmit();
      }
    });
  }

  inviteTokenInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      btnTokenNext.click();
    }
  });
  inviteTokenInput.focus();
  if (inviteTokenInput.value) {
    inviteTokenInput.select();
  }

  bindPassphraseEnter(passphraseInput, passphraseConfirmInput, () => btnPassphraseNext.click());

  btnTokenNext.addEventListener('click', async () => {
    hideError(tokenError);
    hideError(setupError);
    const inviteToken = inviteTokenInput.value.trim();
    if (!inviteToken) {
      showError(tokenError, 'Invitation token is required.');
      return;
    }
    btnTokenNext.disabled = true;
    btnTokenNext.textContent = 'Checking…';
    try {
      const response = await fetch(`/keys/household-invite-wrap?invite=${encodeURIComponent(inviteToken)}`);
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(body.error || 'Invalid invitation token.');
      }
      inviteWrap = body;
      stepToken.hidden = true;
      stepPassphrase.hidden = false;
      passphraseInput.focus();
    } catch (error) {
      showError(tokenError, error.message || 'Invalid invitation token.');
    } finally {
      btnTokenNext.disabled = false;
      btnTokenNext.textContent = 'Continue';
    }
  });

  btnPassphraseNext.addEventListener('click', async () => {
    hideError(passphraseError);
    hideError(setupError);
    if (!inviteWrap) {
      showError(setupError, 'Validate your invitation token first.');
      return;
    }
    const passphrase = passphraseInput.value;
    const confirm = passphraseConfirmInput.value;
    if (passphrase.length < AcruetCrypto.MIN_PASSPHRASE_LENGTH) {
      showError(passphraseError, `Passphrase must be at least ${AcruetCrypto.MIN_PASSPHRASE_LENGTH} characters.`);
      return;
    }
    if (passphrase !== confirm) {
      showError(passphraseError, 'Passphrases do not match.');
      return;
    }
    btnPassphraseNext.disabled = true;
    btnPassphraseNext.textContent = 'Joining household…';
    showError(setupError, 'Deriving encryption key — this can take 10–20 seconds. Please wait.');
    setupError.classList.remove('error');
    setupError.style.color = 'var(--muted)';
    try {
      const meResponse = await fetch('/auth/me');
      const me = meResponse.ok ? await meResponse.json() : {};
      setupState = await AcruetCrypto.joinHouseholdKeySetup(
        inviteTokenInput.value.trim(),
        inviteWrap,
        passphrase,
        me.username || '',
      );
      setupError.hidden = true;
      stepPassphrase.hidden = true;
      stepRecovery.hidden = false;
    } catch (error) {
      setupError.style.color = '';
      setupError.classList.add('error');
      showError(setupError, error.message || 'Could not join household encryption.');
      console.error(error);
    } finally {
      btnPassphraseNext.disabled = false;
      btnPassphraseNext.textContent = 'Continue';
    }
  });

  btnDownloadRecovery.addEventListener('click', () => {
    if (!setupState) {
      return;
    }
    AcruetCrypto.downloadRecoveryFile(setupState.recovery);
    recoveryStatus.textContent = 'Recovery file downloaded. Store it somewhere safe.';
    recoveryStatus.hidden = false;
  });

  recoveryConfirmed.addEventListener('change', () => {
    btnFinishSetup.disabled = !recoveryConfirmed.checked;
  });

  btnFinishSetup.addEventListener('click', async () => {
    hideError(recoveryError);
    hideError(setupError);
    if (!setupState) {
      showError(setupError, 'Complete passphrase setup first.');
      return;
    }
    if (!recoveryConfirmed.checked) {
      showError(recoveryError, 'Confirm that you saved your recovery file.');
      return;
    }
    try {
      const storeResponse = await fetch('/keys/wrapped-dek', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(setupState.dualPayload),
      });
      if (!storeResponse.ok) {
        const body = await storeResponse.json();
        throw new Error(body.error || 'Failed to store wrapped key.');
      }
      const confirmResponse = await fetch('/keys/confirm-recovery', { method: 'POST' });
      if (!confirmResponse.ok) {
        const body = await confirmResponse.json();
        throw new Error(body.error || 'Failed to confirm recovery.');
      }
      AcruetCrypto.session.lock();
      window.location.assign('/');
    } catch (error) {
      showError(setupError, error.message || 'Setup failed. Try again.');
      console.error(error);
    }
  });
});
