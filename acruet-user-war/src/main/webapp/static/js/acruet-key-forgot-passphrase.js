document.addEventListener('DOMContentLoaded', () => {
  const recoveryFileInput = document.getElementById('recoveryFile');
  const newPassphraseInput = document.getElementById('newPassphrase');
  const newPassphraseConfirmInput = document.getElementById('newPassphraseConfirm');
  const fileError = document.getElementById('fileError');
  const resetError = document.getElementById('resetError');
  const resetSuccess = document.getElementById('resetSuccess');
  const btnResetPassphrase = document.getElementById('btnResetPassphrase');

  function showError(element, message) {
    element.textContent = message;
    element.hidden = false;
    resetSuccess.hidden = true;
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

  async function submitReset() {
    fileError.hidden = true;
    resetError.hidden = true;
    resetSuccess.hidden = true;

    const file = recoveryFileInput.files && recoveryFileInput.files[0];
    const newPassphrase = newPassphraseInput.value;
    const confirm = newPassphraseConfirmInput.value;

    if (!file) {
      showError(fileError, 'Choose your recovery file.');
      return;
    }
    if (newPassphrase.length < AcruetCrypto.MIN_PASSPHRASE_LENGTH) {
      showError(resetError, `New passphrase must be at least ${AcruetCrypto.MIN_PASSPHRASE_LENGTH} characters.`);
      return;
    }
    if (newPassphrase !== confirm) {
      showError(resetError, 'New passphrases do not match.');
      return;
    }

    btnResetPassphrase.disabled = true;
    btnResetPassphrase.textContent = 'Resetting…';
    try {
      const fileText = await file.text();
      const resetState = await AcruetCrypto.resetPassphraseFromRecoveryFile(fileText, newPassphrase);
      const resetResponse = await fetch('/keys/reset-passphrase', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(resetState.dualPayload),
      });
      if (!resetResponse.ok) {
        const body = await resetResponse.json();
        throw new Error(body.error || 'Server rejected passphrase reset.');
      }
      AcruetCrypto.downloadRecoveryFile(resetState.recovery);
      AcruetCrypto.session.lock();
      resetSuccess.textContent =
        'Passphrase reset. A new recovery file was downloaded — store it safely, then unlock with your new passphrase.';
      resetSuccess.hidden = false;
    } catch (error) {
      showError(resetError, error.message || 'Reset failed. Check your recovery file.');
      console.error(error);
    } finally {
      btnResetPassphrase.disabled = false;
      btnResetPassphrase.textContent = 'Reset passphrase';
    }
  }

  bindPassphraseEnter(newPassphraseInput, newPassphraseConfirmInput, submitReset);
  btnResetPassphrase.addEventListener('click', submitReset);
  newPassphraseInput.focus();
});
