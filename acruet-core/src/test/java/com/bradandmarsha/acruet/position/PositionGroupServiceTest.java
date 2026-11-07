package com.bradandmarsha.acruet.position;

import com.bradandmarsha.acruet.user.AcruetUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionGroupServiceTest {

    @Test
    void maxActiveGroupsIs20() {
        assertEquals(20, PositionGroupService.MAX_ACTIVE_GROUPS);
    }

    @Test
    void rejectsWhenAtCap() {
        InMemoryPositionGroupStore store = new InMemoryPositionGroupStore();
        PositionGroupService service = new PositionGroupService(store);
        AcruetUser user = sampleUser(true);
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
        for (int index = 0; index < PositionGroupService.MAX_ACTIVE_GROUPS; index += 1) {
            service.create(user, validPayload(), now);
        }
        PositionException exception = assertThrows(
                PositionException.class,
                () -> service.create(user, validPayload(), now));
        assertTrue(exception.getMessage().contains("Group limit reached"));
        assertEquals(PositionGroupService.MAX_ACTIVE_GROUPS, store.countActive(user.householdId()));
    }

    @Test
    void archiveBlockedWhileActiveItemsRemain() {
        InMemoryPositionGroupStore store = new InMemoryPositionGroupStore();
        PositionGroupService service = new PositionGroupService(store);
        AcruetUser user = sampleUser(true);
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
        PositionGroup group = service.create(user, validPayload(), now);
        store.recordActivePosition(group.id(), UUID.randomUUID());
        PositionException exception = assertThrows(
                PositionException.class,
                () -> service.archive(user, group.id()));
        assertTrue(exception.getMessage().contains("Move or ungroup"));
        assertEquals(PositionStatus.ACTIVE, store.findById(user.householdId(), group.id()).orElseThrow().status());
    }

    @Test
    void archiveAllowedWhenEmpty() {
        InMemoryPositionGroupStore store = new InMemoryPositionGroupStore();
        PositionGroupService service = new PositionGroupService(store);
        AcruetUser user = sampleUser(true);
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
        PositionGroup group = service.create(user, validPayload(), now);
        PositionGroup archived = service.archive(user, group.id());
        assertEquals(PositionStatus.ARCHIVED, archived.status());
        assertEquals(0, store.countActive(user.householdId()));
    }

    @Test
    void archivedGroupsDoNotCountTowardCap() {
        InMemoryPositionGroupStore store = new InMemoryPositionGroupStore();
        PositionGroupService service = new PositionGroupService(store);
        AcruetUser user = sampleUser(true);
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
        PositionGroup first = service.create(user, validPayload(), now);
        service.archive(user, first.id());
        for (int index = 0; index < PositionGroupService.MAX_ACTIVE_GROUPS; index += 1) {
            service.create(user, validPayload(), now);
        }
        assertEquals(PositionGroupService.MAX_ACTIVE_GROUPS, store.countActive(user.householdId()));
    }

    @Test
    void cascadeDeleteRemovesOnlyThatHousehold() {
        InMemoryPositionGroupStore store = new InMemoryPositionGroupStore();
        PositionGroupService service = new PositionGroupService(store);
        AcruetUser owner = sampleUser(true);
        AcruetUser other = sampleUser(true);
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
        service.create(owner, validPayload(), now);
        service.create(other, validPayload(), now);
        store.deleteAllForHousehold(owner.householdId());
        assertEquals(0, service.list(owner, true).size());
        assertEquals(1, service.list(other, true).size());
    }

    @Test
    void requiresKeySetup() {
        InMemoryPositionGroupStore store = new InMemoryPositionGroupStore();
        PositionGroupService service = new PositionGroupService(store);
        AcruetUser user = sampleUser(false);
        PositionException exception = assertThrows(
                PositionException.class,
                () -> service.create(user, validPayload(), Instant.parse("2026-11-07T12:00:00Z")));
        assertTrue(exception.getMessage().contains("encryption key"));
    }

    private static String validPayload() {
        return Base64.getEncoder().encodeToString(new byte[28]);
    }

    private static AcruetUser sampleUser(boolean keySetupComplete) {
        Instant now = Instant.parse("2026-11-07T12:00:00Z");
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
                0,
                0,
                keySetupComplete,
                now,
                now,
                now,
                now);
    }
}
