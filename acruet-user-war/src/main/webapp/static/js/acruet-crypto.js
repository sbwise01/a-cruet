/**
 * Client-side envelope encryption primitives (Phase 7 + file-only recovery, Phase 7.1).
 * Passphrase-derived KEK, recovery-secret wrap, and WebAuthn PRF device wrap never send
 * secrets to the server.
 */
const AcruetCrypto = (() => {
  const KDF_ALGORITHM = 'PBKDF2';
  const KDF_HASH = 'SHA-256';
  const WRAP_ALGORITHM = 'AES-KW';
  const DEFAULT_ITERATIONS = 600000;
  const MIN_PASSPHRASE_LENGTH = 12;
  const IDLE_TIMEOUT_MS = 30 * 60 * 1000;
  const RECOVERY_FORMAT = 'acruet-recovery';
  const RECOVERY_VERSION = 2;
  const RECOVERY_SECRET_BYTES = 32;
  const STORAGE_DEK_KEY = 'acruet.session.dek';
  const STORAGE_EXPIRY_KEY = 'acruet.session.expiry';
  const STORAGE_SUBJECT_KEY = 'acruet.session.subject';
  const STORAGE_DEVICE_KEY = 'acruet.unlock.device';
  const PRF_EVAL_FIRST = new TextEncoder().encode('acruet-device-wrap-v1');

  let dek = null;
  let expiryMs = null;
  let activityBound = false;
  let restorePromise = null;

  function clearStorage() {
    sessionStorage.removeItem(STORAGE_DEK_KEY);
    sessionStorage.removeItem(STORAGE_EXPIRY_KEY);
    sessionStorage.removeItem(STORAGE_SUBJECT_KEY);
  }

  async function persistSession(dekKey, subject) {
    const raw = await crypto.subtle.exportKey('raw', dekKey);
    sessionStorage.setItem(STORAGE_DEK_KEY, toBase64(new Uint8Array(raw)));
    expiryMs = Date.now() + IDLE_TIMEOUT_MS;
    sessionStorage.setItem(STORAGE_EXPIRY_KEY, String(expiryMs));
    if (subject) {
      sessionStorage.setItem(STORAGE_SUBJECT_KEY, subject);
    }
    dek = dekKey;
  }

  async function restoreFromStorage() {
    const storedExpiry = Number.parseInt(sessionStorage.getItem(STORAGE_EXPIRY_KEY), 10);
    const rawB64 = sessionStorage.getItem(STORAGE_DEK_KEY);
    if (!storedExpiry || !rawB64 || Date.now() > storedExpiry) {
      session.lock();
      return false;
    }
    try {
      const meResponse = await fetch('/auth/me');
      if (!meResponse.ok) {
        session.lock();
        return false;
      }
      const me = await meResponse.json();
      const storedSubject = sessionStorage.getItem(STORAGE_SUBJECT_KEY);
      if (storedSubject && me.subject !== storedSubject) {
        session.lock();
        return false;
      }
      const raw = fromBase64(rawB64);
      dek = await crypto.subtle.importKey(
        'raw',
        raw,
        { name: 'AES-GCM', length: 256 },
        true,
        ['encrypt', 'decrypt'],
      );
      expiryMs = storedExpiry;
      bindActivity();
      return true;
    } catch (error) {
      session.lock();
      console.error(error);
      return false;
    }
  }

  function encoder() {
    return new TextEncoder();
  }

  function toBase64(bytes) {
    const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('');
    return btoa(binary);
  }

  function fromBase64(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
  }

  function randomSalt(byteLength = 16) {
    const salt = new Uint8Array(byteLength);
    crypto.getRandomValues(salt);
    return salt;
  }

  function randomRecoverySecret() {
    const secret = new Uint8Array(RECOVERY_SECRET_BYTES);
    crypto.getRandomValues(secret);
    return secret;
  }

  async function deriveKek(passphrase, saltBytes, iterations = DEFAULT_ITERATIONS) {
    const keyMaterial = await crypto.subtle.importKey(
      'raw',
      encoder().encode(passphrase),
      KDF_ALGORITHM,
      false,
      ['deriveKey'],
    );
    return crypto.subtle.deriveKey(
      { name: KDF_ALGORITHM, salt: saltBytes, iterations, hash: KDF_HASH },
      keyMaterial,
      { name: WRAP_ALGORITHM, length: 256 },
      false,
      ['wrapKey', 'unwrapKey'],
    );
  }

  async function importRecoveryKey(recoverySecretBytes) {
    return crypto.subtle.importKey(
      'raw',
      recoverySecretBytes,
      { name: WRAP_ALGORITHM },
      false,
      ['wrapKey', 'unwrapKey'],
    );
  }

  async function generateDek() {
    return crypto.subtle.generateKey(
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt'],
    );
  }

  async function wrapDek(kek, dekKey) {
    return crypto.subtle.wrapKey('raw', dekKey, kek, WRAP_ALGORITHM);
  }

  async function unwrapDek(kek, wrappedDekBytes) {
    return crypto.subtle.unwrapKey(
      'raw',
      wrappedDekBytes,
      kek,
      WRAP_ALGORITHM,
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt'],
    );
  }

  function buildRecoveryFile({ recoverySecretBytes, recoveryWrappedDekBase64, emailHint }) {
    return {
      format: RECOVERY_FORMAT,
      version: RECOVERY_VERSION,
      createdAt: new Date().toISOString(),
      email: emailHint || undefined,
      recoverySecret: toBase64(recoverySecretBytes),
      recoveryWrap: {
        algorithm: WRAP_ALGORITHM,
        wrappedDek: recoveryWrappedDekBase64,
      },
    };
  }

  function parseRecoveryFile(raw) {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (!parsed || parsed.format !== RECOVERY_FORMAT) {
      throw new Error('Not a valid a-cruet recovery file.');
    }
    if (parsed.version === 1) {
      throw new Error(
        'This recovery file uses the older format and cannot reset a forgotten passphrase. '
          + 'Use your passphrase, or complete recovery enrollment after signing in.',
      );
    }
    if (parsed.version !== RECOVERY_VERSION) {
      throw new Error('Unsupported recovery file version.');
    }
    if (!parsed.recoverySecret || !parsed.recoveryWrap || !parsed.recoveryWrap.wrappedDek) {
      throw new Error('Recovery file is missing required fields.');
    }
    return {
      recoverySecretBytes: fromBase64(parsed.recoverySecret),
      recoveryWrappedDekBase64: parsed.recoveryWrap.wrappedDek,
    };
  }

  function downloadRecoveryFile(recovery, filename = 'acruet-recovery.json') {
    const blob = new Blob([JSON.stringify(recovery, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function bindActivity() {
    if (activityBound) {
      return;
    }
    const touch = () => session.touch();
    window.addEventListener('mousemove', touch);
    window.addEventListener('keydown', touch);
    window.addEventListener('click', touch);
    activityBound = true;
  }

  function notifyCryptoChanged() {
    document.dispatchEvent(new CustomEvent('acruet:crypto-changed'));
  }

  const session = {
    lock() {
      dek = null;
      expiryMs = null;
      clearStorage();
      notifyCryptoChanged();
    },

    async unlock(passphrase, wrappedPayload) {
      const saltBytes = fromBase64(wrappedPayload.kdfSalt);
      const kek = await deriveKek(passphrase, saltBytes, wrappedPayload.kdfIterations);
      const wrappedDekBytes = fromBase64(wrappedPayload.wrappedDek);
      const dekKey = await unwrapDek(kek, wrappedDekBytes);
      const meResponse = await fetch('/auth/me');
      const subject = meResponse.ok ? (await meResponse.json()).subject : null;
      await persistSession(dekKey, subject);
      bindActivity();
      notifyCryptoChanged();
      return dekKey;
    },

    async ensureReady() {
      if (dek && expiryMs && Date.now() <= expiryMs) {
        return true;
      }
      if (!restorePromise) {
        restorePromise = restoreFromStorage().finally(() => {
          restorePromise = null;
        });
      }
      return restorePromise;
    },

    isUnlocked() {
      const activeExpiry = expiryMs
        || Number.parseInt(sessionStorage.getItem(STORAGE_EXPIRY_KEY), 10);
      if (!dek || !activeExpiry) {
        return false;
      }
      if (Date.now() > activeExpiry) {
        session.lock();
        return false;
      }
      return true;
    },

    touch() {
      if (dek) {
        expiryMs = Date.now() + IDLE_TIMEOUT_MS;
        sessionStorage.setItem(STORAGE_EXPIRY_KEY, String(expiryMs));
      }
    },

    getDek() {
      return session.isUnlocked() ? dek : null;
    },

    async unlockFromDek(dekKey) {
      const meResponse = await fetch('/auth/me');
      const subject = meResponse.ok ? (await meResponse.json()).subject : null;
      await persistSession(dekKey, subject);
      bindActivity();
      notifyCryptoChanged();
      return dekKey;
    },
  };

  function assertWebCrypto() {
    if (!window.crypto || !window.crypto.subtle) {
      throw new Error(
        'Web Crypto is unavailable in this browser context. Use HTTPS and a current browser.',
      );
    }
  }

  async function buildRecoveryWrap(dekKey) {
    const recoverySecretBytes = randomRecoverySecret();
    const recoveryKey = await importRecoveryKey(recoverySecretBytes);
    const recoveryWrappedBuffer = await wrapDek(recoveryKey, dekKey);
    const recoveryWrappedDekBase64 = toBase64(new Uint8Array(recoveryWrappedBuffer));
    return {
      recoverySecretBytes,
      recoveryWrappedDekBase64,
      recoveryPayload: {
        recoveryWrapAlgorithm: WRAP_ALGORITHM,
        recoveryWrappedDek: recoveryWrappedDekBase64,
      },
    };
  }

  async function createDualWrappedDek(passphrase, emailHint) {
    assertWebCrypto();
    const saltBytes = randomSalt();
    const kek = await deriveKek(passphrase, saltBytes, DEFAULT_ITERATIONS);
    const dekKey = await generateDek();
    const wrappedDekBuffer = await wrapDek(kek, dekKey);
    const wrappedDekBase64 = toBase64(new Uint8Array(wrappedDekBuffer));
    const recovery = await buildRecoveryWrap(dekKey);
    return {
      saltBytes,
      iterations: DEFAULT_ITERATIONS,
      wrappedDekBase64,
      dekKey,
      dualPayload: {
        kdfAlgorithm: KDF_ALGORITHM,
        kdfHash: KDF_HASH,
        kdfSalt: toBase64(saltBytes),
        kdfIterations: DEFAULT_ITERATIONS,
        wrapAlgorithm: WRAP_ALGORITHM,
        wrappedDek: wrappedDekBase64,
        recoveryWrapAlgorithm: WRAP_ALGORITHM,
        recoveryWrappedDek: recovery.recoveryWrappedDekBase64,
      },
      recovery: buildRecoveryFile({
        recoverySecretBytes: recovery.recoverySecretBytes,
        recoveryWrappedDekBase64: recovery.recoveryWrappedDekBase64,
        emailHint,
      }),
    };
  }

  async function createDualWrappedDekFromHouseholdDek(dekKey, passphrase, emailHint) {
    assertWebCrypto();
    const saltBytes = randomSalt();
    const kek = await deriveKek(passphrase, saltBytes, DEFAULT_ITERATIONS);
    const wrappedDekBuffer = await wrapDek(kek, dekKey);
    const wrappedDekBase64 = toBase64(new Uint8Array(wrappedDekBuffer));
    const recovery = await buildRecoveryWrap(dekKey);
    return {
      saltBytes,
      iterations: DEFAULT_ITERATIONS,
      wrappedDekBase64,
      dekKey,
      dualPayload: {
        kdfAlgorithm: KDF_ALGORITHM,
        kdfHash: KDF_HASH,
        kdfSalt: toBase64(saltBytes),
        kdfIterations: DEFAULT_ITERATIONS,
        wrapAlgorithm: WRAP_ALGORITHM,
        wrappedDek: wrappedDekBase64,
        recoveryWrapAlgorithm: WRAP_ALGORITHM,
        recoveryWrappedDek: recovery.recoveryWrappedDekBase64,
      },
      recovery: buildRecoveryFile({
        recoverySecretBytes: recovery.recoverySecretBytes,
        recoveryWrappedDekBase64: recovery.recoveryWrappedDekBase64,
        emailHint,
      }),
    };
  }

  async function joinHouseholdKeySetup(inviteToken, inviteWrap, passphrase, emailHint) {
    const dekKey = await unwrapHouseholdInviteDek(inviteToken, inviteWrap.wrappedDek);
    return createDualWrappedDekFromHouseholdDek(dekKey, passphrase, emailHint);
  }

  async function enrollRecoveryWrap(passphrase, wrappedPayload, emailHint) {
    assertWebCrypto();
    const iterations = Number(wrappedPayload.kdfIterations);
    if (!Number.isFinite(iterations) || iterations <= 0) {
      throw new Error('Stored encryption parameters are invalid.');
    }
    const saltBytes = fromBase64(wrappedPayload.kdfSalt);
    let dekKey;
    try {
      const kek = await deriveKek(passphrase, saltBytes, iterations);
      const wrappedDekBytes = fromBase64(wrappedPayload.wrappedDek);
      dekKey = await unwrapDek(kek, wrappedDekBytes);
    } catch (error) {
      if (error && error.name === 'OperationError') {
        throw new Error('Incorrect passphrase.');
      }
      throw error;
    }
    let recovery;
    try {
      recovery = await buildRecoveryWrap(dekKey);
    } catch (error) {
      throw new Error(
        'Could not generate recovery wrap in this browser. Try a current browser over HTTPS.',
        { cause: error },
      );
    }
    return {
      recoveryPayload: {
        recoveryWrapAlgorithm: WRAP_ALGORITHM,
        recoveryWrappedDek: recovery.recoveryWrappedDekBase64,
      },
      recovery: buildRecoveryFile({
        recoverySecretBytes: recovery.recoverySecretBytes,
        recoveryWrappedDekBase64: recovery.recoveryWrappedDekBase64,
        emailHint,
      }),
    };
  }

  async function rotateDualWrappedDek(currentPassphrase, newPassphrase, wrappedPayload, emailHint) {
    const saltBytes = fromBase64(wrappedPayload.kdfSalt);
    const currentKek = await deriveKek(currentPassphrase, saltBytes, wrappedPayload.kdfIterations);
    const wrappedDekBytes = fromBase64(wrappedPayload.wrappedDek);
    const dekKey = await unwrapDek(currentKek, wrappedDekBytes);

    const newSaltBytes = randomSalt();
    const newKek = await deriveKek(newPassphrase, newSaltBytes, DEFAULT_ITERATIONS);
    const newWrappedDekBuffer = await wrapDek(newKek, dekKey);
    const newWrappedDekBase64 = toBase64(new Uint8Array(newWrappedDekBuffer));
    const recovery = await buildRecoveryWrap(dekKey);

    return {
      dualPayload: {
        kdfAlgorithm: KDF_ALGORITHM,
        kdfHash: KDF_HASH,
        kdfSalt: toBase64(newSaltBytes),
        kdfIterations: DEFAULT_ITERATIONS,
        wrapAlgorithm: WRAP_ALGORITHM,
        wrappedDek: newWrappedDekBase64,
        recoveryWrapAlgorithm: WRAP_ALGORITHM,
        recoveryWrappedDek: recovery.recoveryWrappedDekBase64,
      },
      recovery: buildRecoveryFile({
        recoverySecretBytes: recovery.recoverySecretBytes,
        recoveryWrappedDekBase64: recovery.recoveryWrappedDekBase64,
        emailHint,
      }),
      dekKey,
    };
  }

  async function rotateDualWrappedDekFromSession(newPassphrase, emailHint) {
    const dekKey = session.getDek();
    if (!dekKey) {
      throw new Error('Unlock your ledger to change the passphrase without the current one.');
    }
    return createDualWrappedDekFromHouseholdDek(dekKey, newPassphrase, emailHint);
  }

  async function resetPassphraseFromRecoveryFile(recoveryFileRaw, newPassphrase, emailHint) {
    const parsed = parseRecoveryFile(recoveryFileRaw);
    const recoveryKey = await importRecoveryKey(parsed.recoverySecretBytes);
    const wrappedDekBytes = fromBase64(parsed.recoveryWrappedDekBase64);
    const dekKey = await unwrapDek(recoveryKey, wrappedDekBytes);

    const newSaltBytes = randomSalt();
    const newKek = await deriveKek(newPassphrase, newSaltBytes, DEFAULT_ITERATIONS);
    const newWrappedDekBuffer = await wrapDek(newKek, dekKey);
    const newWrappedDekBase64 = toBase64(new Uint8Array(newWrappedDekBuffer));
    const recovery = await buildRecoveryWrap(dekKey);

    return {
      dualPayload: {
        kdfAlgorithm: KDF_ALGORITHM,
        kdfHash: KDF_HASH,
        kdfSalt: toBase64(newSaltBytes),
        kdfIterations: DEFAULT_ITERATIONS,
        wrapAlgorithm: WRAP_ALGORITHM,
        wrappedDek: newWrappedDekBase64,
        recoveryWrapAlgorithm: WRAP_ALGORITHM,
        recoveryWrappedDek: recovery.recoveryWrappedDekBase64,
      },
      recovery: buildRecoveryFile({
        recoverySecretBytes: recovery.recoverySecretBytes,
        recoveryWrappedDekBase64: recovery.recoveryWrappedDekBase64,
        emailHint,
      }),
    };
  }

  async function encryptJson(dekKey, value) {
    assertWebCrypto();
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encoded = encoder().encode(JSON.stringify(value));
    const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, dekKey, encoded);
    const combined = new Uint8Array(iv.length + ciphertext.byteLength);
    combined.set(iv, 0);
    combined.set(new Uint8Array(ciphertext), iv.length);
    return toBase64(combined);
  }

  async function decryptJson(dekKey, base64) {
    assertWebCrypto();
    const combined = fromBase64(base64);
    const iv = combined.slice(0, 12);
    const data = combined.slice(12);
    const decrypted = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, dekKey, data);
    return JSON.parse(new TextDecoder().decode(decrypted));
  }

  function inviteTokenToBytes(inviteToken) {
    const normalized = inviteToken.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
    return fromBase64(padded);
  }

  function newInviteToken() {
    const bytes = crypto.getRandomValues(new Uint8Array(32));
    return toBase64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  async function buildHouseholdInviteWrap(dekKey, inviteToken) {
    assertWebCrypto();
    const tokenBytes = inviteTokenToBytes(inviteToken);
    const inviteKey = await crypto.subtle.importKey(
      'raw',
      tokenBytes,
      { name: WRAP_ALGORITHM },
      false,
      ['wrapKey', 'unwrapKey'],
    );
    const wrappedBuffer = await wrapDek(inviteKey, dekKey);
    const saltBytes = randomSalt();
    return {
      wrappedDek: toBase64(new Uint8Array(wrappedBuffer)),
      wrapAlgorithm: WRAP_ALGORITHM,
      kdfAlgorithm: KDF_ALGORITHM,
      kdfHash: KDF_HASH,
      kdfSalt: toBase64(saltBytes),
      kdfIterations: 0,
    };
  }

  async function unwrapHouseholdInviteDek(inviteToken, wrappedDekBase64) {
    assertWebCrypto();
    const tokenBytes = inviteTokenToBytes(inviteToken);
    const inviteKey = await crypto.subtle.importKey(
      'raw',
      tokenBytes,
      { name: WRAP_ALGORITHM },
      false,
      ['wrapKey', 'unwrapKey'],
    );
    return unwrapDek(inviteKey, fromBase64(wrappedDekBase64));
  }

  function toBase64Url(bytes) {
    return toBase64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  function fromBase64Url(value) {
    const normalized = String(value).replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
    return fromBase64(padded);
  }

  function fromBase64Flexible(value) {
    if (!value) {
      throw new Error('Missing encoded value.');
    }
    return fromBase64Url(value);
  }

  async function importPrfWrapKey(prfBytes) {
    let keyBytes = prfBytes instanceof Uint8Array ? prfBytes : new Uint8Array(prfBytes);
    if (keyBytes.byteLength !== 16 && keyBytes.byteLength !== 24 && keyBytes.byteLength !== 32) {
      keyBytes = new Uint8Array(await crypto.subtle.digest('SHA-256', keyBytes));
    }
    return crypto.subtle.importKey(
      'raw',
      keyBytes,
      { name: WRAP_ALGORITHM },
      false,
      ['wrapKey', 'unwrapKey'],
    );
  }

  async function wrapDekWithPrf(prfBytes, dekKey) {
    assertWebCrypto();
    const kek = await importPrfWrapKey(prfBytes);
    const wrapped = await wrapDek(kek, dekKey);
    return toBase64(new Uint8Array(wrapped));
  }

  async function unwrapDekWithPrf(prfBytes, wrappedDekBase64) {
    assertWebCrypto();
    const kek = await importPrfWrapKey(prfBytes);
    return unwrapDek(kek, fromBase64Flexible(wrappedDekBase64));
  }

  function decodeBufferField(value) {
    if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
      return value;
    }
    return fromBase64Url(value);
  }

  function decodePublicKeyOptions(publicKey) {
    const decoded = { ...publicKey };
    decoded.challenge = decodeBufferField(publicKey.challenge);
    if (publicKey.user && publicKey.user.id) {
      decoded.user = { ...publicKey.user, id: decodeBufferField(publicKey.user.id) };
    }
    if (Array.isArray(publicKey.excludeCredentials)) {
      decoded.excludeCredentials = publicKey.excludeCredentials.map((credential) => ({
        ...credential,
        id: decodeBufferField(credential.id),
      }));
    }
    if (Array.isArray(publicKey.allowCredentials)) {
      decoded.allowCredentials = publicKey.allowCredentials.map((credential) => ({
        ...credential,
        id: decodeBufferField(credential.id),
      }));
    }
    const first = publicKey.extensions && publicKey.extensions.prf
      && publicKey.extensions.prf.eval && publicKey.extensions.prf.eval.first;
    decoded.extensions = {
      ...(publicKey.extensions || {}),
      prf: {
        eval: {
          first: first ? decodeBufferField(first) : PRF_EVAL_FIRST.slice(),
        },
      },
    };
    return decoded;
  }

  function prfFromCredential(credential) {
    if (!credential || typeof credential.getClientExtensionResults !== 'function') {
      return null;
    }
    const results = credential.getClientExtensionResults();
    const first = results && results.prf && results.prf.results && results.prf.results.first;
    if (!first) {
      return null;
    }
    return first instanceof Uint8Array ? first : new Uint8Array(first);
  }

  async function platformAuthenticatorAvailable() {
    if (!window.PublicKeyCredential
        || typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable !== 'function') {
      return false;
    }
    try {
      return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    } catch (error) {
      console.error(error);
      return false;
    }
  }

  async function prfExtensionSupported() {
    if (typeof PublicKeyCredential.getClientCapabilities !== 'function') {
      return null;
    }
    try {
      const capabilities = await PublicKeyCredential.getClientCapabilities();
      if (!capabilities || typeof capabilities !== 'object') {
        return null;
      }
      if (Object.prototype.hasOwnProperty.call(capabilities, 'extension:prf')) {
        return Boolean(capabilities['extension:prf']);
      }
      if (capabilities.extensions && Object.prototype.hasOwnProperty.call(capabilities.extensions, 'prf')) {
        return Boolean(capabilities.extensions.prf);
      }
      return null;
    } catch (error) {
      console.error(error);
      return null;
    }
  }

  async function supportsDeviceUnlock() {
    if (!window.PublicKeyCredential
        || !navigator.credentials
        || typeof navigator.credentials.create !== 'function') {
      return false;
    }
    // iOS 26: isUVPAA() is false unless a third-party passkey provider is on.
    // That hid Add on Safari when Authenticator was off, which is the only
    // path that can reach Apple Face ID. Offer Add whenever WebAuthn create
    // exists; create() + PRF results are the real gate.
    return true;
  }

  function defaultBrowserName() {
    const userAgent = navigator.userAgent || '';
    if (/Edg\//.test(userAgent) || /EdgiOS/.test(userAgent)) {
      return 'Edge';
    }
    if (/OPR\/|Opera/.test(userAgent)) {
      return 'Opera';
    }
    if (/Firefox|FxiOS/.test(userAgent)) {
      return 'Firefox';
    }
    if (/CriOS/.test(userAgent) || (/Chrome|Chromium/.test(userAgent) && !/Edg/.test(userAgent))) {
      return 'Chrome';
    }
    if (/Safari/.test(userAgent) && !/Chrome|Chromium|CriOS|Android/.test(userAgent)) {
      return 'Safari';
    }
    return '';
  }

  function defaultDeviceName() {
    const userAgent = navigator.userAgent || '';
    let platform = 'This device';
    if (/iPhone/.test(userAgent)) {
      platform = 'iPhone';
    } else if (/iPad/.test(userAgent)) {
      platform = 'iPad';
    } else if (/Macintosh|Mac OS X/.test(userAgent)) {
      platform = 'Mac';
    } else if (/Android/.test(userAgent)) {
      platform = 'Android';
    } else if (/Windows/.test(userAgent)) {
      platform = 'Windows PC';
    }
    const browser = defaultBrowserName();
    if (!browser || platform === 'This device') {
      return browser || platform;
    }
    return platform + ' ' + browser;
  }

  function storedDevice() {
    try {
      const raw = localStorage.getItem(STORAGE_DEVICE_KEY);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw);
      if (!parsed || !parsed.deviceId || !parsed.credentialId) {
        return null;
      }
      return parsed;
    } catch (error) {
      return null;
    }
  }

  function rememberDevice({ credentialId, deviceId, subject }) {
    localStorage.setItem(STORAGE_DEVICE_KEY, JSON.stringify({
      credentialId,
      deviceId,
      subject: subject || null,
    }));
  }

  function forgetDevice(deviceId) {
    const stored = storedDevice();
    if (!stored || !deviceId || stored.deviceId === deviceId) {
      localStorage.removeItem(STORAGE_DEVICE_KEY);
    }
  }

  function thisDeviceIsRegistered(devices, subject) {
    const stored = storedDevice();
    if (!stored) {
      return false;
    }
    if (subject && stored.subject && stored.subject !== subject) {
      localStorage.removeItem(STORAGE_DEVICE_KEY);
      return false;
    }
    return (devices || []).some((device) => device.id === stored.deviceId);
  }

  async function evaluatePrfLocally(rawId) {
    try {
      const assertion = await navigator.credentials.get({
        publicKey: {
          challenge: crypto.getRandomValues(new Uint8Array(32)),
          rpId: window.location.hostname,
          allowCredentials: [{ type: 'public-key', id: rawId }],
          userVerification: 'required',
          timeout: 120000,
          extensions: { prf: { eval: { first: PRF_EVAL_FIRST.slice() } } },
        },
      });
      return prfFromCredential(assertion);
    } catch (error) {
      console.error(error);
      return null;
    }
  }

  function webAuthnUserMessage(error, cancelledMessage, failedMessage) {
    if (error && (error.name === 'NotAllowedError' || error.name === 'AbortError')) {
      return cancelledMessage;
    }
    return (error && error.message) || failedMessage;
  }

  async function createPlatformCredential(publicKeyOptions) {
    const decoded = decodePublicKeyOptions(publicKeyOptions);
    let credential;
    try {
      credential = await navigator.credentials.create({ publicKey: decoded });
    } catch (error) {
      throw new Error(webAuthnUserMessage(
        error,
        'Registration cancelled.',
        error.message || 'Could not register this device.',
      ));
    }
    if (!credential) {
      throw new Error('Registration cancelled.');
    }
    let prfBytes = prfFromCredential(credential);
    if (!prfBytes) {
      prfBytes = await evaluatePrfLocally(credential.rawId);
    }
    if (!prfBytes) {
      throw new Error(
        'This browser cannot derive a device wrap key. Use passphrase unlock on this device.',
      );
    }
    return {
      credential,
      credentialId: toBase64Url(new Uint8Array(credential.rawId)),
      clientData: toBase64(new Uint8Array(credential.response.clientDataJSON)),
      attestation: toBase64(new Uint8Array(credential.response.attestationObject)),
      prfBytes,
    };
  }

  function platformUnlockLabel() {
    const userAgent = navigator.userAgent || '';
    if (/iPhone|iPad/.test(userAgent)) {
      return 'Unlock with Face ID';
    }
    if (/Macintosh|Mac OS X/.test(userAgent)) {
      return 'Unlock with Touch ID';
    }
    if (/Windows/.test(userAgent)) {
      return 'Unlock with Windows Hello';
    }
    return 'Unlock with this device';
  }

  async function getPlatformAssertion(publicKeyOptions) {
    const decoded = decodePublicKeyOptions(publicKeyOptions);
    let assertion;
    try {
      assertion = await navigator.credentials.get({ publicKey: decoded });
    } catch (error) {
      throw new Error(webAuthnUserMessage(
        error,
        'Unlock cancelled.',
        error.message || 'Could not unlock with this device.',
      ));
    }
    if (!assertion) {
      throw new Error('Unlock cancelled.');
    }
    const prfBytes = prfFromCredential(assertion);
    if (!prfBytes) {
      throw new Error(
        'This browser cannot derive a device wrap key. Use passphrase unlock on this device.',
      );
    }
    return {
      assertion,
      credentialId: toBase64Url(new Uint8Array(assertion.rawId)),
      clientData: toBase64(new Uint8Array(assertion.response.clientDataJSON)),
      authenticatorData: toBase64(new Uint8Array(assertion.response.authenticatorData)),
      signature: toBase64(new Uint8Array(assertion.response.signature)),
      prfBytes,
    };
  }

  async function currentSubject() {
    const meResponse = await fetch('/auth/me');
    if (!meResponse.ok) {
      return null;
    }
    const me = await meResponse.json();
    return me.subject || null;
  }

  async function registerThisDevice() {
    const dekKey = session.getDek();
    if (!dekKey) {
      throw new Error('Unlock your ledger to add this device.');
    }
    const optionsResponse = await fetch('/keys/devices/attestation-options', { method: 'POST' });
    if (!optionsResponse.ok) {
      const body = await optionsResponse.json().catch(() => ({}));
      throw new Error(body.error || 'Could not start device registration.');
    }
    const options = await optionsResponse.json();
    const created = await createPlatformCredential(options.publicKey);
    const wrappedDek = await wrapDekWithPrf(created.prfBytes, dekKey);
    const registerResponse = await fetch('/keys/devices', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        credentialId: created.credentialId,
        clientData: created.clientData,
        attestation: created.attestation,
        wrapAlgorithm: WRAP_ALGORITHM,
        wrappedDek,
        displayName: defaultDeviceName(),
      }),
    });
    if (!registerResponse.ok) {
      const body = await registerResponse.json().catch(() => ({}));
      throw new Error(body.error || 'Could not save this device.');
    }
    const view = await registerResponse.json();
    rememberDevice({
      credentialId: created.credentialId,
      deviceId: view.id,
      subject: await currentSubject(),
    });
    return view;
  }

  async function unlockWithThisDevice(publicKey) {
    const asserted = await getPlatformAssertion(publicKey);
    const unlockResponse = await fetch('/keys/devices/unlock', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        credentialId: asserted.credentialId,
        clientData: asserted.clientData,
        authenticatorData: asserted.authenticatorData,
        signature: asserted.signature,
      }),
    });
    if (!unlockResponse.ok) {
      const body = await unlockResponse.json().catch(() => ({}));
      if (unlockResponse.status === 404) {
        forgetDevice();
      }
      throw new Error(body.error || 'Could not unlock with this device.');
    }
    const unlocked = await unlockResponse.json();
    const dekKey = await unwrapDekWithPrf(asserted.prfBytes, unlocked.wrappedDek);
    await session.unlockFromDek(dekKey);
    return dekKey;
  }

  const deviceUnlock = {
    PRF_EVAL_FIRST,
    supportsDeviceUnlock,
    defaultDeviceName,
    storedDevice,
    rememberDevice,
    forgetDevice,
    thisDeviceIsRegistered,
    decodePublicKeyOptions,
    prfFromCredential,
    createPlatformCredential,
    getPlatformAssertion,
    platformUnlockLabel,
    registerThisDevice,
    unlockWithThisDevice,
    wrapDekWithPrf,
    unwrapDekWithPrf,
    toBase64Url,
    fromBase64Url,
    fromBase64Flexible,
  };

  return {
    KDF_ALGORITHM,
    KDF_HASH,
    WRAP_ALGORITHM,
    DEFAULT_ITERATIONS,
    MIN_PASSPHRASE_LENGTH,
    IDLE_TIMEOUT_MS,
    RECOVERY_VERSION,
    deriveKek,
    generateDek,
    wrapDek,
    unwrapDek,
    randomSalt,
    parseRecoveryFile,
    buildRecoveryFile,
    downloadRecoveryFile,
    toBase64,
    fromBase64,
    createDualWrappedDek,
    createDualWrappedDekFromHouseholdDek,
    joinHouseholdKeySetup,
    enrollRecoveryWrap,
    rotateDualWrappedDek,
    rotateDualWrappedDekFromSession,
    resetPassphraseFromRecoveryFile,
    newInviteToken,
    buildHouseholdInviteWrap,
    unwrapHouseholdInviteDek,
    encryptJson,
    decryptJson,
    session,
    deviceUnlock,
  };
})();
