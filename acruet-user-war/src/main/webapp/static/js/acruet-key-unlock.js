document.addEventListener('DOMContentLoaded', async () => {
  const passphraseInput = document.getElementById('passphrase');
  const unlockError = document.getElementById('unlockError');
  const btnUnlock = document.getElementById('btnUnlock');

  function goNext() {
    const params = new URLSearchParams(window.location.search);
    window.location.assign(params.get('next') || '/');
  }

  await AcruetCrypto.session.ensureReady();
  if (AcruetCrypto.session.isUnlocked()) {
    goNext();
    return;
  }

  AcruetDeviceUnlock.attachPage({
    passphraseInput,
    errorEl: unlockError,
    unlockButton: btnUnlock,
    onUnlocked: goNext,
  });
});
