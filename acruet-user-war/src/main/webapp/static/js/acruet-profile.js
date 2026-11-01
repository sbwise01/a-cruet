document.addEventListener('DOMContentLoaded', async () => {
  const displayNameInput = document.getElementById('displayName');
  const emailInput = document.getElementById('email');
  const phoneInput = document.getElementById('phone');
  const mailingAddressInput = document.getElementById('mailingAddress');
  const allowNegativeWithdrawInput = document.getElementById('allowNegativeWithdraw');
  const profileError = document.getElementById('profileError');
  const profileSuccess = document.getElementById('profileSuccess');
  const btnSaveProfile = document.getElementById('btnSaveProfile');
  const deviceList = document.getElementById('deviceList');
  const deviceEmpty = document.getElementById('deviceEmpty');
  const deviceLockedHint = document.getElementById('deviceLockedHint');
  const deviceUnsupportedHint = document.getElementById('deviceUnsupportedHint');
  const deviceSetupHint = document.getElementById('deviceSetupHint');
  const deviceIosHint = document.getElementById('deviceIosHint');
  const deviceError = document.getElementById('deviceError');
  const deviceSuccess = document.getElementById('deviceSuccess');
  const btnAddDevice = document.getElementById('btnAddDevice');
  const nav = document.getElementById('userNav');
  const keySetupComplete = !nav || nav.dataset.keySetup === 'true';

  let devices = [];
  let subject = null;
  let platformSupported = false;

  function showError(message) {
    profileError.textContent = message;
    profileError.hidden = false;
    profileSuccess.hidden = true;
  }

  function showSuccess(message) {
    profileSuccess.textContent = message;
    profileSuccess.hidden = false;
    profileError.hidden = true;
  }

  function showDeviceError(message) {
    deviceError.textContent = message;
    deviceError.hidden = false;
    deviceSuccess.hidden = true;
  }

  function showDeviceSuccess(message) {
    deviceSuccess.textContent = message;
    deviceSuccess.hidden = false;
    deviceError.hidden = true;
  }

  function clearDeviceStatus() {
    deviceError.hidden = true;
    deviceSuccess.hidden = true;
  }

  function formatAdded(iso) {
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return 'added ' + date.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  }

  function renderDevices() {
    deviceList.replaceChildren();
    devices.forEach((device) => {
      const item = document.createElement('li');
      item.className = 'device-row';
      item.dataset.deviceId = device.id;
      const meta = document.createElement('div');
      meta.className = 'device-meta';
      const nameButton = document.createElement('button');
      nameButton.type = 'button';
      nameButton.className = 'device-name';
      nameButton.dataset.rename = 'true';
      nameButton.textContent = device.displayName;
      const added = document.createElement('p');
      added.className = 'added';
      added.textContent = formatAdded(device.createdAt);
      meta.append(nameButton, added);
      const actions = document.createElement('div');
      actions.className = 'device-actions';
      const removeButton = document.createElement('button');
      removeButton.type = 'button';
      removeButton.dataset.remove = 'true';
      removeButton.textContent = 'Remove';
      actions.append(removeButton);
      item.append(meta, actions);
      deviceList.append(item);
    });
    deviceEmpty.hidden = devices.length > 0 || !keySetupComplete;
    updateAddVisibility();
  }

  function updateAddVisibility() {
    const unlocked = AcruetCrypto.session.isUnlocked();
    const atCap = devices.length >= 10;
    const alreadyRegistered = AcruetCrypto.deviceUnlock.thisDeviceIsRegistered(devices, subject);
    const canAdd = keySetupComplete && platformSupported && unlocked && !alreadyRegistered && !atCap;
    btnAddDevice.hidden = !canAdd;
    deviceLockedHint.hidden = !keySetupComplete || !platformSupported || unlocked
      || alreadyRegistered || atCap;
    deviceUnsupportedHint.hidden = !keySetupComplete || platformSupported;
    deviceSetupHint.hidden = keySetupComplete;
    if (deviceIosHint) {
      const userAgent = navigator.userAgent || '';
      deviceIosHint.hidden = !/iPhone|iPad/.test(userAgent);
    }
  }

  async function loadDevices() {
    if (!keySetupComplete) {
      devices = [];
      renderDevices();
      return;
    }
    const response = await fetch('/keys/devices');
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Could not load unlock devices.');
    }
    const payload = await response.json();
    devices = Array.isArray(payload.devices) ? payload.devices : [];
    if (subject) {
      AcruetCrypto.deviceUnlock.thisDeviceIsRegistered(devices, subject);
    }
    renderDevices();
  }

  async function registerThisDevice() {
    clearDeviceStatus();
    if (!AcruetCrypto.session.isUnlocked()) {
      showDeviceError('Unlock your ledger to add this device.');
      return;
    }
    const dekKey = AcruetCrypto.session.getDek();
    btnAddDevice.disabled = true;
    btnAddDevice.textContent = 'Waiting for this device…';
    try {
      const optionsResponse = await fetch('/keys/devices/attestation-options', { method: 'POST' });
      if (!optionsResponse.ok) {
        const body = await optionsResponse.json().catch(() => ({}));
        throw new Error(body.error || 'Could not start device registration.');
      }
      const options = await optionsResponse.json();
      const created = await AcruetCrypto.deviceUnlock.createPlatformCredential(options.publicKey);
      const wrappedDek = await AcruetCrypto.deviceUnlock.wrapDekWithPrf(created.prfBytes, dekKey);
      const registerResponse = await fetch('/keys/devices', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          credentialId: created.credentialId,
          clientData: created.clientData,
          attestation: created.attestation,
          wrapAlgorithm: AcruetCrypto.WRAP_ALGORITHM,
          wrappedDek,
          displayName: AcruetCrypto.deviceUnlock.defaultDeviceName(),
        }),
      });
      if (!registerResponse.ok) {
        const body = await registerResponse.json().catch(() => ({}));
        throw new Error(body.error || 'Could not save this device.');
      }
      const view = await registerResponse.json();
      AcruetCrypto.deviceUnlock.rememberDevice({
        credentialId: created.credentialId,
        deviceId: view.id,
        subject,
      });
      showDeviceSuccess('This device can unlock next time.');
      await loadDevices();
    } catch (error) {
      showDeviceError(error.message || 'Could not add this device.');
      console.error(error);
    } finally {
      btnAddDevice.disabled = false;
      btnAddDevice.textContent = 'Add this device';
    }
  }

  async function removeDevice(deviceId, displayName) {
    const label = displayName || 'this device';
    if (!window.confirm('Remove ' + label + '? That device will need the passphrase to unlock.')) {
      return;
    }
    clearDeviceStatus();
    try {
      const response = await fetch('/keys/devices/' + encodeURIComponent(deviceId), { method: 'DELETE' });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.error || 'Could not remove device.');
      }
      AcruetCrypto.deviceUnlock.forgetDevice(deviceId);
      showDeviceSuccess('Device removed.');
      await loadDevices();
    } catch (error) {
      showDeviceError(error.message || 'Could not remove device.');
      console.error(error);
    }
  }

  async function renameDevice(deviceId, displayName) {
    const trimmed = displayName.trim();
    if (!trimmed) {
      showDeviceError('Enter a device name.');
      return;
    }
    clearDeviceStatus();
    try {
      const response = await fetch('/keys/devices/' + encodeURIComponent(deviceId), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName: trimmed }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.error || 'Could not rename device.');
      }
      await loadDevices();
    } catch (error) {
      showDeviceError(error.message || 'Could not rename device.');
      console.error(error);
    }
  }

  function startRename(row) {
    const deviceId = row.dataset.deviceId;
    const nameButton = row.querySelector('.device-name');
    if (!deviceId || !nameButton) {
      return;
    }
    const current = nameButton.textContent;
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'device-name-input';
    input.maxLength = 80;
    input.value = current;
    nameButton.replaceWith(input);
    input.focus();
    input.select();
    let finished = false;
    const finish = async (save) => {
      if (finished) {
        return;
      }
      finished = true;
      if (save && input.value.trim() && input.value.trim() !== current) {
        await renameDevice(deviceId, input.value);
        return;
      }
      renderDevices();
    };
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        finish(true);
      } else if (event.key === 'Escape') {
        event.preventDefault();
        finish(false);
      }
    });
    input.addEventListener('blur', () => finish(true));
  }

  try {
    const response = await fetch('/profile/api');
    if (!response.ok) {
      throw new Error('Could not load profile.');
    }
    const profile = await response.json();
    displayNameInput.value = profile.displayName || '';
    emailInput.value = profile.email || '';
    phoneInput.value = profile.phone || '';
    mailingAddressInput.value = profile.mailingAddress || '';
    allowNegativeWithdrawInput.checked = Boolean(profile.allowNegativeWithdraw);
  } catch (error) {
    showError(error.message || 'Could not load profile.');
    console.error(error);
  }

  btnSaveProfile.addEventListener('click', async () => {
    profileError.hidden = true;
    profileSuccess.hidden = true;
    btnSaveProfile.disabled = true;
    btnSaveProfile.textContent = 'Saving…';
    try {
      const response = await fetch('/profile/api', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: displayNameInput.value,
          phone: phoneInput.value,
          mailingAddress: mailingAddressInput.value,
          allowNegativeWithdraw: allowNegativeWithdrawInput.checked,
        }),
      });
      if (!response.ok) {
        const body = await response.json();
        throw new Error(body.error || 'Failed to save profile.');
      }
      showSuccess('Profile saved.');
      window.setTimeout(() => window.location.assign('/'), 800);
    } catch (error) {
      showError(error.message || 'Failed to save profile.');
      console.error(error);
    } finally {
      btnSaveProfile.disabled = false;
      btnSaveProfile.textContent = 'Save profile';
    }
  });

  deviceList.addEventListener('click', (event) => {
    const removeButton = event.target.closest('[data-remove]');
    const renameButton = event.target.closest('[data-rename]');
    const row = event.target.closest('.device-row');
    if (!row) {
      return;
    }
    if (removeButton) {
      const nameButton = row.querySelector('.device-name');
      removeDevice(row.dataset.deviceId, nameButton ? nameButton.textContent : '');
      return;
    }
    if (renameButton) {
      startRename(row);
    }
  });

  btnAddDevice.addEventListener('click', registerThisDevice);
  document.addEventListener('acruet:crypto-changed', updateAddVisibility);

  try {
    await AcruetCrypto.session.ensureReady();
    const meResponse = await fetch('/auth/me');
    if (meResponse.ok) {
      const me = await meResponse.json();
      subject = me.subject || null;
    }
    platformSupported = await AcruetCrypto.deviceUnlock.supportsDeviceUnlock();
    await loadDevices();
  } catch (error) {
    showDeviceError(error.message || 'Could not load unlock devices.');
    console.error(error);
    updateAddVisibility();
  }
});
