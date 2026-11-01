package com.bradandmarsha.acruet.keys;

import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.ledger.LedgerTransactionRepository;
import com.bradandmarsha.acruet.ledger.LedgerWriteRateLimiter;
import com.bradandmarsha.acruet.user.AcruetUser;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Register, list, rename, remove, and unlock via stored device wraps.
 */
public final class DeviceUnlockService {

    public static final int MAX_DEVICES = 10;

    private final UnlockDeviceStore store;
    private final WebAuthnSettings webAuthnSettings;
    private final LedgerWriteRateLimiter rateLimiter;

    public DeviceUnlockService(UnlockDeviceStore store, WebAuthnSettings webAuthnSettings) {
        this(store, webAuthnSettings, null);
    }

    public DeviceUnlockService(
            UnlockDeviceStore store,
            WebAuthnSettings webAuthnSettings,
            LedgerWriteRateLimiter rateLimiter) {
        this.store = Objects.requireNonNull(store, "store");
        this.webAuthnSettings = Objects.requireNonNull(webAuthnSettings, "webAuthnSettings");
        this.rateLimiter = rateLimiter;
    }

    public static DeviceUnlockService fromEnvironment() {
        return new DeviceUnlockService(
                new UserUnlockDeviceRepository(),
                WebAuthnSettings.fromEnvironment(),
                new LedgerWriteRateLimiter(new LedgerTransactionRepository()));
    }

    public List<DeviceView> list(AcruetUser user) {
        requireKeySetup(user);
        List<DeviceView> views = new ArrayList<>();
        for (UserUnlockDevice device : store.listByUser(user.id())) {
            views.add(DeviceView.from(device));
        }
        return views;
    }

    public IssuedOptions attestationOptions(AcruetUser user) {
        requireKeySetup(user);
        byte[] challenge = WebAuthnOptions.newChallenge();
        return new IssuedOptions(challenge, WebAuthnOptions.attestation(webAuthnSettings, user, challenge));
    }

    public IssuedOptions assertionOptions(AcruetUser user) {
        requireKeySetup(user);
        byte[] challenge = WebAuthnOptions.newChallenge();
        return new IssuedOptions(
                challenge, WebAuthnOptions.assertion(webAuthnSettings, store.listByUser(user.id()), challenge));
    }

    public DeviceView register(AcruetUser user, DeviceWrapPayload payload, Instant now) {
        requireKeySetup(user);
        payload.validationError().ifPresent(message -> {
            throw new DeviceUnlockException(message);
        });
        checkWriteRate(user.id());
        if (store.countByUser(user.id()) >= MAX_DEVICES) {
            throw new DeviceUnlockException("Device limit reached (" + MAX_DEVICES + ").");
        }
        byte[] credentialId = payload.credentialIdBytes();
        Optional<UserUnlockDevice> existing = store.findByCredentialId(credentialId);
        if (existing.isPresent()) {
            throw new DeviceUnlockException("That authenticator is already registered.");
        }
        UserUnlockDevice device = new UserUnlockDevice(
                UUID.randomUUID(),
                user.id(),
                credentialId,
                payload.wrappedDekBytes(),
                payload.wrapAlgorithm(),
                payload.sanitizedDisplayName(),
                now,
                now);
        store.insert(device);
        return DeviceView.from(device);
    }

    public DeviceView rename(AcruetUser user, UUID deviceId, String displayName) {
        requireKeySetup(user);
        checkWriteRate(user.id());
        DeviceWrapPayload namePayload = new DeviceWrapPayload(
                DeviceWrapPayload.WRAP_ALGORITHM,
                Base64.getEncoder().encodeToString(new byte[DeviceWrapPayload.MIN_WRAPPED_DEK_BYTES]),
                Base64.getEncoder().encodeToString(new byte[DeviceWrapPayload.MIN_CREDENTIAL_ID_BYTES]),
                displayName);
        String sanitized = namePayload.sanitizedDisplayName();
        if (!store.updateDisplayName(user.id(), deviceId, sanitized)) {
            throw new DeviceUnlockException("Device not found.");
        }
        return store.findById(user.id(), deviceId)
                .map(DeviceView::from)
                .orElseThrow(() -> new DeviceUnlockException("Device not found."));
    }

    public void remove(AcruetUser user, UUID deviceId) {
        requireKeySetup(user);
        checkWriteRate(user.id());
        if (!store.delete(user.id(), deviceId)) {
            throw new DeviceUnlockException("Device not found.");
        }
    }

    public UnlockPayload unlock(AcruetUser user, String credentialIdBase64, Instant now) {
        requireKeySetup(user);
        byte[] credentialId = DeviceWrapPayload.decodeFlexible(credentialIdBase64);
        if (credentialId == null) {
            throw new DeviceUnlockException("Credential id is invalid.");
        }
        UserUnlockDevice device = store.findByCredentialId(credentialId)
                .filter(found -> found.userId().equals(user.id()))
                .orElseThrow(() -> new DeviceUnlockException("Device not found."));
        store.touchLastUsed(user.id(), device.id(), now);
        return new UnlockPayload(
                WebAuthnOptions.encode(device.wrappedDek()),
                device.wrapAlgorithm(),
                WebAuthnOptions.encode(WebAuthnSettings.PRF_EVAL_FIRST));
    }

    public WebAuthnSettings settings() {
        return webAuthnSettings;
    }

    private static void requireKeySetup(AcruetUser user) {
        if (!user.keySetupComplete()) {
            throw new DeviceUnlockException("Complete key setup before managing devices.");
        }
    }

    private void checkWriteRate(UUID userId) {
        if (rateLimiter == null) {
            return;
        }
        try {
            Database.inTransaction(connection -> {
                LedgerWriteRateLimiter.RateLimitResult result = rateLimiter.check(connection, userId, Instant.now());
                if (!result.permitted()) {
                    throw new DeviceUnlockException(result.message());
                }
                rateLimiter.record(connection, userId);
            });
        } catch (DeviceUnlockException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new DeviceUnlockException("Failed to apply write rate limit", exception);
        }
    }

    public record DeviceView(UUID id, String displayName, Instant createdAt, Instant lastUsedAt) {
        static DeviceView from(UserUnlockDevice device) {
            return new DeviceView(device.id(), device.displayName(), device.createdAt(), device.lastUsedAt());
        }
    }

    public record IssuedOptions(byte[] challenge, Map<String, Object> publicKey) {
    }

    public record UnlockPayload(String wrappedDek, String wrapAlgorithm, String prfEvalFirst) {
    }
}
