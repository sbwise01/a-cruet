document.addEventListener('DOMContentLoaded', () => {
  const passphraseInput = document.getElementById('passphrase');
  const unlockError = document.getElementById('unlockError');
  const btnUnlock = document.getElementById('btnUnlock');

  function showError(message) {
    unlockError.textContent = message;
    unlockError.hidden = false;
  }

  btnUnlock.addEventListener('click', async () => {
    unlockError.hidden = true;
    const passphrase = passphraseInput.value;
    if (!passphrase) {
      showError('Enter your passphrase.');
      return;
    }
    try {
      const response = await fetch('/keys/wrapped-dek');
      if (!response.ok) {
        throw new Error('Could not load wrapped encryption key.');
      }
      const payload = await response.json();
      await AcruetCrypto.session.unlock(passphrase, payload);
      window.location.assign('/');
    } catch (error) {
      showError('Incorrect passphrase or unlock failed.');
      console.error(error);
    }
  });
});
