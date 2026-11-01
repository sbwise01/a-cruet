package com.bradandmarsha.acruet.networth;

import com.bradandmarsha.acruet.user.AcruetUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetWorthServiceTest {

    @Test
    void maxSnapshotsIs600() {
        assertEquals(600, NetWorthService.MAX_SNAPSHOTS);
    }

    @Test
    void rejectsWhenAtCap() {
        InMemoryNetWorthStore store = new InMemoryNetWorthStore();
        NetWorthService service = new NetWorthService(store);
        AcruetUser user = sampleUser(true);
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        for (int index = 0; index < NetWorthService.MAX_SNAPSHOTS; index += 1) {
            service.create(user, LocalDate.of(2000, 1, 1).plusDays(index), validPayload(), now);
        }
        NetWorthException exception = assertThrows(
                NetWorthException.class,
                () -> service.create(user, LocalDate.of(2026, 11, 1), validPayload(), now));
        assertTrue(exception.getMessage().contains("Net worth limit reached"));
        assertEquals(NetWorthService.MAX_SNAPSHOTS, store.countByHousehold(user.householdId()));
    }

    @Test
    void latestPrefersNewerAsOfDateThenNewerCreatedAt() {
        InMemoryNetWorthStore store = new InMemoryNetWorthStore();
        NetWorthService service = new NetWorthService(store);
        AcruetUser user = sampleUser(true);
        Instant early = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-06-01T00:00:00Z");
        Instant latestSameDay = Instant.parse("2026-06-01T12:00:00Z");
        service.create(user, LocalDate.of(2024, 12, 31), validPayload(), later);
        NetWorthSnapshot olderSameDay = service.create(user, LocalDate.of(2025, 12, 31), validPayload(), early);
        NetWorthSnapshot newerSameDay = service.create(user, LocalDate.of(2025, 12, 31), validPayload(), latestSameDay);
        assertEquals(newerSameDay.id(), service.latest(user).orElseThrow().id());
        assertEquals(3, service.list(user).size());
        assertEquals(olderSameDay.asOfDate(), service.list(user).get(1).asOfDate());
        assertEquals(newerSameDay.id(), service.list(user).get(2).id());
    }

    @Test
    void cascadeDeleteRemovesOnlyThatHousehold() {
        InMemoryNetWorthStore store = new InMemoryNetWorthStore();
        NetWorthService service = new NetWorthService(store);
        AcruetUser owner = sampleUser(true);
        AcruetUser other = sampleUser(true);
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        service.create(owner, LocalDate.of(2025, 12, 31), validPayload(), now);
        service.create(owner, LocalDate.of(2024, 12, 31), validPayload(), now);
        service.create(other, LocalDate.of(2025, 12, 31), validPayload(), now);
        store.deleteAllForHousehold(owner.householdId());
        assertEquals(0, service.list(owner).size());
        assertEquals(1, service.list(other).size());
    }

    @Test
    void requiresKeySetup() {
        InMemoryNetWorthStore store = new InMemoryNetWorthStore();
        NetWorthService service = new NetWorthService(store);
        AcruetUser user = sampleUser(false);
        NetWorthException exception = assertThrows(
                NetWorthException.class,
                () -> service.create(
                        user, LocalDate.of(2026, 11, 1), validPayload(), Instant.parse("2026-11-01T12:00:00Z")));
        assertTrue(exception.getMessage().contains("encryption key"));
    }

    private static String validPayload() {
        return Base64.getEncoder().encodeToString(new byte[28]);
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
