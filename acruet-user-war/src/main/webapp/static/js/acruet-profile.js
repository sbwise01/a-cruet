document.addEventListener('DOMContentLoaded', async () => {
  const displayNameInput = document.getElementById('displayName');
  const emailInput = document.getElementById('email');
  const phoneInput = document.getElementById('phone');
  const mailingAddressInput = document.getElementById('mailingAddress');
  const allowNegativeWithdrawInput = document.getElementById('allowNegativeWithdraw');
  const profileError = document.getElementById('profileError');
  const profileSuccess = document.getElementById('profileSuccess');
  const btnSaveProfile = document.getElementById('btnSaveProfile');

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
});
