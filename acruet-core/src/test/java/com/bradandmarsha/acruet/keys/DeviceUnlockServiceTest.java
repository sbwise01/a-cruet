package com.bradandmarsha.acruet.keys;

import com.bradandmarsha.acruet.user.AcruetUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceUnlockServiceTest {

    @Test
    void maxDevicesIsTen() {
        assertEquals(10, DeviceUnlockService.MAX_DEVICES);
    }

    @Test
    void rejectsEleventhDevice() {
        InMemoryUnlockDeviceStore store = new InMemoryUnlockDeviceStore();
        DeviceUnlockService service = service(store);
        AcruetUser user = sampleUser(true);
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        for (int index = 0; index < DeviceUnlockService.MAX_DEVICES; index += 1) {
            service.register(user, wrapPayload(credentialId(index), "Device " + index), now);
        }
        DeviceUnlockException exception = assertThrows(
                DeviceUnlockException.class,
                () -> service.register(user, wrapPayload(credentialId(99), "Overflow"), now));
        assertTrue(exception.getMessage().contains("Device limit reached"));
    }

    @Test
    void rejectsDuplicateCredentialId() {
        InMemoryUnlockDeviceStore store = new InMemoryUnlockDeviceStore();
        DeviceUnlockService service = service(store);
        AcruetUser first = sampleUser(true);
        AcruetUser second = sampleUser(true);
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        byte[] credential = credentialId(1);
        service.register(first, wrapPayload(credential, "Kitchen iPad"), now);
        DeviceUnlockException exception = assertThrows(
                DeviceUnlockException.class,
                () -> service.register(second, wrapPayload(credential, "Other"), now));
        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    void cascadeDeleteRemovesOnlyThatUsersDevices() {
        InMemoryUnlockDeviceStore store = new InMemoryUnlockDeviceStore();
        DeviceUnlockService service = service(store);
        AcruetUser owner = sampleUser(true);
        AcruetUser other = sampleUser(true);
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        service.register(owner, wrapPayload(credentialId(1), "Mac"), now);
        service.register(owner, wrapPayload(credentialId(2), "Phone"), now);
        service.register(other, wrapPayload(credentialId(3), "Other phone"), now);
        store.deleteAllForUser(owner.id());
        assertEquals(0, service.list(owner).size());
        assertEquals(1, service.list(other).size());
    }

    @Test
    void listOmitsWrappedDek() {
        InMemoryUnlockDeviceStore store = new InMemoryUnlockDeviceStore();
        DeviceUnlockService service = service(store);
        AcruetUser user = sampleUser(true);
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        DeviceUnlockService.DeviceView view =
                service.register(user, wrapPayload(credentialId(1), "MacBook"), now);
        assertEquals("MacBook", view.displayName());
        assertEquals(1, service.list(user).size());
        assertEquals("MacBook", service.list(user).get(0).displayName());
    }

    @Test
    void attestationOptionsUseUserHostname() {
        DeviceUnlockService service = service(new InMemoryUnlockDeviceStore());
        DeviceUnlockService.IssuedOptions options = service.attestationOptions(sampleUser(true));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> rp = (java.util.Map<String, Object>) options.publicKey().get("rp");
        assertEquals("acruet.home.bradandmarsha.com", rp.get("id"));
    }

    private static DeviceUnlockService service(InMemoryUnlockDeviceStore store) {
        return new DeviceUnlockService(
                store, WebAuthnSettings.fromBaseUrl("https://acruet.home.bradandmarsha.com"));
    }

    private static DeviceWrapPayload wrapPayload(byte[] credentialId, String displayName) {
        return new DeviceWrapPayload(
                DeviceWrapPayload.WRAP_ALGORITHM,
                Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(credentialId),
                displayName);
    }

    private static byte[] credentialId(int seed) {
        byte[] credential = new byte[32];
        for (int index = 0; index < credential.length; index += 1) {
            credential[index] = (byte) (seed + index);
        }
        return credential;
    }

    private static AcruetUser sampleUser(boolean keySetupComplete) {
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        return new AcruetUser(
                UUID.randomUUID(),
                "kc-" + UUID.randomUUID(),
                "owner@example.com",
                "Owner Example",
                null,
                "555-0100",
                "1 Main St",
                false,
                UUID.randomUUID(),
                0,
                0,
                100,
                1,
                0,
                keySetupComplete,
                now,
                now,
                now,
                now);
    }
}
