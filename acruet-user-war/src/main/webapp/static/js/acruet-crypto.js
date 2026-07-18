/**
 * Client-side envelope encryption primitives (Phase 7 + file-only recovery, Phase 7.1).
 * Passphrase-derived KEK and recovery-secret wrap never send secrets to the server.
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
    enrollRecoveryWrap,
    rotateDualWrappedDek,
    resetPassphraseFromRecoveryFile,
    encryptJson,
    decryptJson,
    session,
  };
})();
