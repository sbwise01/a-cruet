document.addEventListener('DOMContentLoaded', () => {
  const currentPassphraseInput = document.getElementById('currentPassphrase');
  const newPassphraseInput = document.getElementById('newPassphrase');
  const newPassphraseConfirmInput = document.getElementById('newPassphraseConfirm');
  const rotateError = document.getElementById('rotateError');
  const rotateSuccess = document.getElementById('rotateSuccess');
  const btnRotate = document.getElementById('btnRotate');

  function showError(message) {
    rotateError.textContent = message;
    rotateError.hidden = false;
    rotateSuccess.hidden = true;
  }

  btnRotate.addEventListener('click', async () => {
    rotateError.hidden = true;
    rotateSuccess.hidden = true;

    const currentPassphrase = currentPassphraseInput.value;
    const newPassphrase = newPassphraseInput.value;
    const confirm = newPassphraseConfirmInput.value;

    if (!currentPassphrase) {
      showError('Enter your current passphrase.');
      return;
    }
    if (newPassphrase.length < AcruetCrypto.MIN_PASSPHRASE_LENGTH) {
      showError(`New passphrase must be at least ${AcruetCrypto.MIN_PASSPHRASE_LENGTH} characters.`);
      return;
    }
    if (newPassphrase !== confirm) {
      showError('New passphrases do not match.');
      return;
    }

    try {
      const response = await fetch('/keys/wrapped-dek');
      if (!response.ok) {
        throw new Error('Could not load wrapped encryption key.');
      }
      const payload = await response.json();
      const rotated = await AcruetCrypto.rotateWrappedDek(currentPassphrase, newPassphrase, payload);

      const rotateResponse = await fetch('/keys/rotate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(rotated.payload),
      });
      if (!rotateResponse.ok) {
        const body = await rotateResponse.json();
        throw new Error(body.error || 'Server rejected key rotation.');
      }

      AcruetCrypto.downloadRecoveryFile(rotated.recovery);
      AcruetCrypto.session.lock();
      rotateSuccess.textContent =
        'Key rotated successfully. A new recovery file was downloaded — store it safely, then unlock with your new passphrase.';
      rotateSuccess.hidden = false;
    } catch (error) {
      showError(error.message || 'Rotation failed. Check your current passphrase.');
      console.error(error);
    }
  });
});
