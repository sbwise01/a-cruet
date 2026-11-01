document.addEventListener('DOMContentLoaded', async () => {
  const currentPassphraseBlock = document.getElementById('currentPassphraseBlock');
  const currentPassphraseInput = document.getElementById('currentPassphrase');
  const newPassphraseInput = document.getElementById('newPassphrase');
  const newPassphraseConfirmInput = document.getElementById('newPassphraseConfirm');
  const rotateHint = document.getElementById('rotateHint');
  const rotateError = document.getElementById('rotateError');
  const rotateSuccess = document.getElementById('rotateSuccess');
  const btnRotate = document.getElementById('btnRotate');

  await AcruetCrypto.session.ensureReady();
  const unlocked = AcruetCrypto.session.isUnlocked();
  if (unlocked && currentPassphraseBlock) {
    currentPassphraseBlock.hidden = true;
  }
  if (unlocked && rotateHint) {
    rotateHint.textContent =
      'Your ledger is unlocked. Set a new passphrase without entering the current one. '
      + 'Ledger ciphertext is unchanged. A new recovery file is downloaded afterward. Device unlocks stay valid.';
  }

  function showError(message) {
    rotateError.textContent = message;
    rotateError.hidden = false;
    rotateSuccess.hidden = true;
  }

  function bindEnterChain(fields, onSubmit) {
    fields.forEach((field, index) => {
      field.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter') {
          return;
        }
        event.preventDefault();
        if (index < fields.length - 1) {
          fields[index + 1].focus();
        } else {
          onSubmit();
        }
      });
    });
  }

  async function submitRotate() {
    rotateError.hidden = true;
    rotateSuccess.hidden = true;

    const currentPassphrase = currentPassphraseInput ? currentPassphraseInput.value : '';
    const newPassphrase = newPassphraseInput.value;
    const confirm = newPassphraseConfirmInput.value;

    if (!unlocked && !currentPassphrase) {
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
      let rotated;
      if (unlocked) {
        rotated = await AcruetCrypto.rotateDualWrappedDekFromSession(newPassphrase);
      } else {
        const response = await fetch('/keys/wrapped-dek');
        if (!response.ok) {
          throw new Error('Could not load wrapped encryption key.');
        }
        const payload = await response.json();
        rotated = await AcruetCrypto.rotateDualWrappedDek(currentPassphrase, newPassphrase, payload);
      }

      const rotateResponse = await fetch('/keys/rotate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(rotated.dualPayload),
      });
      if (!rotateResponse.ok) {
        const body = await rotateResponse.json();
        throw new Error(body.error || 'Server rejected key rotation.');
      }

      AcruetCrypto.downloadRecoveryFile(rotated.recovery);
      if (unlocked && rotated.dekKey) {
        await AcruetCrypto.session.unlockFromDek(rotated.dekKey);
      } else {
        AcruetCrypto.session.lock();
      }
      rotateSuccess.textContent = unlocked
        ? 'Passphrase changed. A new recovery file was downloaded — store it safely. Device unlocks still work.'
        : 'Key rotated successfully. A new recovery file was downloaded — store it safely, then unlock with your new passphrase.';
      rotateSuccess.hidden = false;
    } catch (error) {
      showError(error.message || (unlocked ? 'Rotation failed.' : 'Rotation failed. Check your current passphrase.'));
      console.error(error);
    }
  }

  const enterFields = unlocked
    ? [newPassphraseInput, newPassphraseConfirmInput]
    : [currentPassphraseInput, newPassphraseInput, newPassphraseConfirmInput];
  bindEnterChain(enterFields, submitRotate);
  btnRotate.addEventListener('click', submitRotate);
  if (unlocked) {
    newPassphraseInput.focus();
  } else {
    currentPassphraseInput.focus();
  }
});
