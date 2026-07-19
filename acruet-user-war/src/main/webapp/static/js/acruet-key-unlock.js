document.addEventListener('DOMContentLoaded', () => {
  const passphraseInput = document.getElementById('passphrase');
  const unlockError = document.getElementById('unlockError');
  const btnUnlock = document.getElementById('btnUnlock');

  function showError(message) {
    unlockError.textContent = message;
    unlockError.hidden = false;
  }

  async function submitUnlock() {
    unlockError.hidden = true;
    btnUnlock.disabled = true;
    btnUnlock.textContent = 'Unlocking…';
    const passphrase = passphraseInput.value;
    if (!passphrase) {
      showError('Enter your passphrase.');
      btnUnlock.disabled = false;
      btnUnlock.textContent = 'Unlock';
      return;
    }
    try {
      const response = await fetch('/keys/wrapped-dek');
      if (!response.ok) {
        throw new Error('Could not load wrapped encryption key.');
      }
      const payload = await response.json();
      await AcruetCrypto.session.unlock(passphrase, payload);
      const params = new URLSearchParams(window.location.search);
      window.location.assign(params.get('next') || '/');
    } catch (error) {
      showError('Incorrect passphrase or unlock failed.');
      console.error(error);
    } finally {
      btnUnlock.disabled = false;
      btnUnlock.textContent = 'Unlock';
    }
  }

  btnUnlock.addEventListener('click', submitUnlock);
  passphraseInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      submitUnlock();
    }
  });
});
