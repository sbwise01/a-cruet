/**
 * Client-side envelope encryption primitives (Phase 7).
 * Passphrase-derived KEK and wrapped DEK never send the passphrase to the server.
 */
const AcruetCrypto = (() => {
  const KDF_ALGORITHM = 'PBKDF2';
  const KDF_HASH = 'SHA-256';
  const WRAP_ALGORITHM = 'AES-KW';
  const DEFAULT_ITERATIONS = 600000;
  const MIN_PASSPHRASE_LENGTH = 12;
  const IDLE_TIMEOUT_MS = 30 * 60 * 1000;
  const RECOVERY_FORMAT = 'acruet-recovery';
  const RECOVERY_VERSION = 1;

  let dek = null;
  let expiryMs = null;
  let activityBound = false;

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

  function buildRecoveryFile({ saltBytes, iterations, wrappedDekBase64 }) {
    return {
      format: RECOVERY_FORMAT,
      version: RECOVERY_VERSION,
      createdAt: new Date().toISOString(),
      kdf: {
        algorithm: KDF_ALGORITHM,
        hash: KDF_HASH,
        salt: toBase64(saltBytes),
        iterations,
      },
      wrap: {
        algorithm: WRAP_ALGORITHM,
        wrappedDek: wrappedDekBase64,
      },
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

  const session = {
    lock() {
      dek = null;
      expiryMs = null;
    },

    async unlock(passphrase, wrappedPayload) {
      const saltBytes = fromBase64(wrappedPayload.kdfSalt);
      const kek = await deriveKek(passphrase, saltBytes, wrappedPayload.kdfIterations);
      const wrappedDekBytes = fromBase64(wrappedPayload.wrappedDek);
      dek = await unwrapDek(kek, wrappedDekBytes);
      expiryMs = Date.now() + IDLE_TIMEOUT_MS;
      bindActivity();
      return dek;
    },

    isUnlocked() {
      if (!dek || !expiryMs) {
        return false;
      }
      if (Date.now() > expiryMs) {
        session.lock();
        return false;
      }
      return true;
    },

    touch() {
      if (dek) {
        expiryMs = Date.now() + IDLE_TIMEOUT_MS;
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

  async function createWrappedDek(passphrase) {
    assertWebCrypto();
    const saltBytes = randomSalt();
    const kek = await deriveKek(passphrase, saltBytes, DEFAULT_ITERATIONS);
    const dekKey = await generateDek();
    const wrappedDekBuffer = await wrapDek(kek, dekKey);
    const wrappedDekBase64 = toBase64(new Uint8Array(wrappedDekBuffer));
    return {
      saltBytes,
      iterations: DEFAULT_ITERATIONS,
      wrappedDekBase64,
      dekKey,
      payload: {
        kdfAlgorithm: KDF_ALGORITHM,
        kdfHash: KDF_HASH,
        kdfSalt: toBase64(saltBytes),
        kdfIterations: DEFAULT_ITERATIONS,
        wrapAlgorithm: WRAP_ALGORITHM,
        wrappedDek: wrappedDekBase64,
      },
      recovery: buildRecoveryFile({
        saltBytes,
        iterations: DEFAULT_ITERATIONS,
        wrappedDekBase64,
      }),
    };
  }

  async function rotateWrappedDek(currentPassphrase, newPassphrase, wrappedPayload) {
    const saltBytes = fromBase64(wrappedPayload.kdfSalt);
    const currentKek = await deriveKek(currentPassphrase, saltBytes, wrappedPayload.kdfIterations);
    const wrappedDekBytes = fromBase64(wrappedPayload.wrappedDek);
    const dekKey = await unwrapDek(currentKek, wrappedDekBytes);

    const newSaltBytes = randomSalt();
    const newKek = await deriveKek(newPassphrase, newSaltBytes, DEFAULT_ITERATIONS);
    const newWrappedDekBuffer = await wrapDek(newKek, dekKey);
    const newWrappedDekBase64 = toBase64(new Uint8Array(newWrappedDekBuffer));

    return {
      payload: {
        kdfAlgorithm: KDF_ALGORITHM,
        kdfHash: KDF_HASH,
        kdfSalt: toBase64(newSaltBytes),
        kdfIterations: DEFAULT_ITERATIONS,
        wrapAlgorithm: WRAP_ALGORITHM,
        wrappedDek: newWrappedDekBase64,
      },
      recovery: buildRecoveryFile({
        saltBytes: newSaltBytes,
        iterations: DEFAULT_ITERATIONS,
        wrappedDekBase64: newWrappedDekBase64,
      }),
      dekKey,
    };
  }

  return {
    KDF_ALGORITHM,
    KDF_HASH,
    WRAP_ALGORITHM,
    DEFAULT_ITERATIONS,
    MIN_PASSPHRASE_LENGTH,
    IDLE_TIMEOUT_MS,
    deriveKek,
    generateDek,
    wrapDek,
    unwrapDek,
    randomSalt,
    buildRecoveryFile,
    downloadRecoveryFile,
    toBase64,
    fromBase64,
    createWrappedDek,
    rotateWrappedDek,
    session,
  };
})();
