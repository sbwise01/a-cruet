document.addEventListener('DOMContentLoaded', () => {
  const passphraseInput = document.getElementById('passphrase');
  const passphraseError = document.getElementById('passphraseError');
  const enrollError = document.getElementById('enrollError');
  const recoveryError = document.getElementById('recoveryError');
  const recoveryStatus = document.getElementById('recoveryStatus');
  const recoveryConfirmed = document.getElementById('recoveryConfirmed');
  const btnGenerateRecovery = document.getElementById('btnGenerateRecovery');
  const btnDownloadRecovery = document.getElementById('btnDownloadRecovery');
  const btnFinishEnroll = document.getElementById('btnFinishEnroll');
  const stepRecovery = document.getElementById('step-recovery');

  let enrollState = null;

  function showError(element, message) {
    element.textContent = message;
    element.hidden = false;
  }

  function hideError(element) {
    element.hidden = true;
    element.textContent = '';
  }

  passphraseInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      btnGenerateRecovery.click();
    }
  });
  passphraseInput.focus();

  btnGenerateRecovery.addEventListener('click', async () => {
    hideError(passphraseError);
    hideError(enrollError);
    const passphrase = passphraseInput.value;
    if (!passphrase) {
      showError(passphraseError, 'Enter your current passphrase.');
      return;
    }
    if (typeof AcruetCrypto === 'undefined' || typeof AcruetCrypto.enrollRecoveryWrap !== 'function') {
      showError(
        enrollError,
        'Encryption scripts failed to load. Hard-refresh the page or check /static/js/acruet-crypto.js in the network tab.',
      );
      return;
    }
    btnGenerateRecovery.disabled = true;
    btnGenerateRecovery.textContent = 'Verifying…';
    showError(
      enrollError,
      'Verifying passphrase — key derivation can take 10–20 seconds. Please wait.',
    );
    enrollError.classList.remove('error');
    enrollError.style.color = 'var(--muted)';
    try {
      const response = await fetch('/keys/wrapped-dek');
      if (!response.ok) {
        throw new Error(`Could not load your encryption key (HTTP ${response.status}).`);
      }
      const payload = await response.json();
      enrollState = await AcruetCrypto.enrollRecoveryWrap(passphrase, payload);
      hideError(enrollError);
      stepRecovery.hidden = false;
      btnGenerateRecovery.hidden = true;
      passphraseInput.disabled = true;
    } catch (error) {
      hideError(enrollError);
      enrollError.style.color = '';
      enrollError.classList.add('error');
      showError(passphraseError, error.message || 'Recovery enrollment failed.');
      console.error(error);
    } finally {
      btnGenerateRecovery.disabled = false;
      btnGenerateRecovery.textContent = 'Continue';
    }
  });

  btnDownloadRecovery.addEventListener('click', () => {
    if (!enrollState) {
      return;
    }
    AcruetCrypto.downloadRecoveryFile(enrollState.recovery);
    recoveryStatus.textContent = 'Recovery file downloaded. Store it somewhere safe.';
    recoveryStatus.hidden = false;
  });

  recoveryConfirmed.addEventListener('change', () => {
    btnFinishEnroll.disabled = !recoveryConfirmed.checked;
  });

  btnFinishEnroll.addEventListener('click', async () => {
    hideError(recoveryError);
    hideError(enrollError);
    if (!enrollState) {
      showError(enrollError, 'Verify your passphrase first.');
      return;
    }
    if (!recoveryConfirmed.checked) {
      showError(recoveryError, 'Confirm that you saved your recovery file.');
      return;
    }
    try {
      const enrollResponse = await fetch('/keys/enroll-recovery', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(enrollState.recoveryPayload),
      });
      if (!enrollResponse.ok) {
        const body = await enrollResponse.json();
        throw new Error(body.error || 'Failed to enroll recovery wrap.');
      }
      window.location.assign('/');
    } catch (error) {
      showError(enrollError, error.message || 'Enrollment failed.');
      console.error(error);
    }
  });
});
