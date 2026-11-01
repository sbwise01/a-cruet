/**
 * Unlock-form device primary action, passphrase fallback, and post-passphrase offer.
 */
const AcruetDeviceUnlock = (() => {
  let cachedAssertion = null;
  let prefetchPromise = null;

  function byId(id) {
    return document.getElementById(id);
  }

  function prefetchAssertionIfRegistered() {
    if (prefetchPromise) {
      return prefetchPromise;
    }
    prefetchPromise = (async () => {
      if (!AcruetCrypto.deviceUnlock.storedDevice()) {
        cachedAssertion = null;
        return false;
      }
      const response = await fetch('/keys/devices/assertion-options', { method: 'POST' });
      if (!response.ok) {
        cachedAssertion = null;
        return false;
      }
      cachedAssertion = await response.json();
      return Boolean(cachedAssertion && cachedAssertion.publicKey);
    })().finally(() => {
      prefetchPromise = null;
    });
    return prefetchPromise;
  }

  function show(el, visible) {
    if (el) {
      el.hidden = !visible;
    }
  }

  function setError(errorEl, message) {
    if (!errorEl) {
      return;
    }
    if (!message) {
      errorEl.hidden = true;
      errorEl.textContent = '';
      return;
    }
    errorEl.textContent = message;
    errorEl.hidden = false;
  }

  function layout(registered, showingPassphrase, showingOffer) {
    const primary = byId('deviceUnlockPrimary');
    const passphraseBlock = byId('passphraseUnlockBlock');
    const offer = byId('offerRegisterDevice');
    const deviceButton = byId('btnDeviceUnlock');
    if (deviceButton) {
      deviceButton.textContent = AcruetCrypto.deviceUnlock.platformUnlockLabel();
    }
    show(primary, registered && !showingPassphrase && !showingOffer);
    show(passphraseBlock, (!registered || showingPassphrase) && !showingOffer);
    show(offer, showingOffer);
  }

  async function tryDeviceUnlock(errorEl) {
    let publicKey = cachedAssertion && cachedAssertion.publicKey;
    if (!publicKey) {
      const ready = await prefetchAssertionIfRegistered();
      publicKey = ready && cachedAssertion ? cachedAssertion.publicKey : null;
    }
    if (!publicKey) {
      throw new Error('This device is not registered for unlock.');
    }
    cachedAssertion = null;
    setError(errorEl, '');
    await AcruetCrypto.deviceUnlock.unlockWithThisDevice(publicKey);
  }

  function attachInline(options) {
    const form = options.form;
    const tile = options.tile;
    const passphraseInput = options.passphraseInput;
    const errorEl = options.errorEl;
    const unlockButton = options.unlockButton;
    const cancelButton = options.cancelButton;
    const deviceButton = byId('btnDeviceUnlock');
    const usePassphraseButton = byId('btnUsePassphrase');
    const offerRegister = byId('btnOfferRegister');
    const offerDecline = byId('btnOfferDecline');
    let showingPassphrase = false;
    let offerPending = false;

    prefetchAssertionIfRegistered();

    async function finishUnlocked() {
      offerPending = false;
      if (options.onUnlocked) {
        options.onUnlocked();
      }
    }

    async function onDeviceUnlock() {
      if (deviceButton) {
        deviceButton.disabled = true;
      }
      try {
        await tryDeviceUnlock(errorEl);
        await finishUnlocked();
      } catch (error) {
        setError(errorEl, error.message || 'Could not unlock with this device.');
        showingPassphrase = true;
        layout(true, true, false);
        console.error(error);
      } finally {
        if (deviceButton) {
          deviceButton.disabled = false;
        }
      }
    }

    async function onPassphraseUnlock() {
      setError(errorEl, '');
      const passphrase = passphraseInput ? passphraseInput.value : '';
      if (!passphrase) {
        setError(errorEl, 'Enter your passphrase.');
        return;
      }
      if (unlockButton) {
        unlockButton.disabled = true;
        unlockButton.textContent = 'Unlocking…';
      }
      try {
        const response = await fetch('/keys/wrapped-dek');
        if (!response.ok) {
          throw new Error('Could not load wrapped encryption key.');
        }
        const payload = await response.json();
        await AcruetCrypto.session.unlock(passphrase, payload);
        const canOffer = await AcruetCrypto.deviceUnlock.supportsDeviceUnlock()
          && !AcruetCrypto.deviceUnlock.storedDevice();
        if (canOffer) {
          offerPending = true;
          showingPassphrase = false;
          layout(false, false, true);
          return;
        }
        await finishUnlocked();
      } catch (error) {
        setError(errorEl, 'Incorrect passphrase or unlock failed.');
        console.error(error);
      } finally {
        if (unlockButton) {
          unlockButton.disabled = false;
          unlockButton.textContent = 'Unlock';
        }
      }
    }

    async function showForm(startDeviceUnlock) {
      if (!form) {
        return;
      }
      if (tile && tile.closest('.unlock-tile-wrap')) {
        tile.closest('.unlock-tile-wrap').hidden = true;
      }
      form.hidden = false;
      setError(errorEl, '');
      const registered = Boolean(AcruetCrypto.deviceUnlock.storedDevice());
      showingPassphrase = !registered;
      offerPending = false;
      layout(registered, showingPassphrase, false);
      if (registered && startDeviceUnlock) {
        await onDeviceUnlock();
        return;
      }
      if (passphraseInput && showingPassphrase) {
        passphraseInput.value = '';
        passphraseInput.focus();
      }
    }

    function hideForm() {
      if (form) {
        form.hidden = true;
      }
      if (tile && tile.closest('.unlock-tile-wrap')) {
        tile.closest('.unlock-tile-wrap').hidden = false;
      }
      setError(errorEl, '');
      offerPending = false;
    }

    if (tile) {
      tile.addEventListener('click', () => showForm(true));
    }
    if (unlockButton) {
      unlockButton.addEventListener('click', onPassphraseUnlock);
    }
    if (passphraseInput) {
      passphraseInput.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          onPassphraseUnlock();
        }
      });
    }
    if (cancelButton) {
      cancelButton.addEventListener('click', () => {
        if (offerPending) {
          finishUnlocked();
          return;
        }
        hideForm();
        if (options.onCancel) {
          options.onCancel();
        }
      });
    }
    if (deviceButton) {
      deviceButton.addEventListener('click', onDeviceUnlock);
    }
    if (usePassphraseButton) {
      usePassphraseButton.addEventListener('click', () => {
        showingPassphrase = true;
        layout(true, true, false);
        if (passphraseInput) {
          passphraseInput.focus();
        }
      });
    }
    if (offerRegister) {
      offerRegister.addEventListener('click', async () => {
        offerRegister.disabled = true;
        try {
          await AcruetCrypto.deviceUnlock.registerThisDevice();
          await finishUnlocked();
        } catch (error) {
          setError(errorEl, error.message || 'Could not add this device.');
          console.error(error);
        } finally {
          offerRegister.disabled = false;
        }
      });
    }
    if (offerDecline) {
      offerDecline.addEventListener('click', () => finishUnlocked());
    }

    return {
      showForm: () => showForm(true),
      hideForm,
      prefetch: prefetchAssertionIfRegistered,
    };
  }

  function attachPage(options) {
    const controls = attachInline(options);
    const registered = Boolean(AcruetCrypto.deviceUnlock.storedDevice());
    layout(registered, !registered, false);
    controls.prefetch();
    return controls;
  }

  return {
    attachInline,
    attachPage,
    prefetchAssertionIfRegistered,
    platformUnlockLabel: () => AcruetCrypto.deviceUnlock.platformUnlockLabel(),
  };
})();
