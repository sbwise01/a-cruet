package com.bradandmarsha.acruet.admin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserOffboardTest {

    @Test
    void notDueWhileWindowOpen() {
        Instant now = Instant.parse("2026-11-21T15:00:00Z");
        UserOffboard offboard = sample(now.plusSeconds(3600), null, null);
        assertTrue(offboard.isActive());
        assertFalse(offboard.isDueForPurge(now));
    }

    @Test
    void dueWhenDeadlinePasses() {
        Instant now = Instant.parse("2026-11-21T15:00:00Z");
        UserOffboard offboard = sample(now.minusSeconds(1), null, null);
        assertTrue(offboard.isDueForPurge(now));
    }

    @Test
    void dueWhenExportCompleteEvenIfDeadlineFuture() {
        Instant now = Instant.parse("2026-11-21T15:00:00Z");
        UserOffboard offboard = sample(now.plusSeconds(3600), now, null);
        assertTrue(offboard.isDueForPurge(now));
    }

    private static UserOffboard sample(Instant deadline, Instant exportCompletedAt, Instant purgedAt) {
        return new UserOffboard(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                Instant.parse("2026-11-21T14:00:00Z"),
                deadline,
                exportCompletedAt,
                purgedAt,
                "admin-kc",
                "admin@example.com");
    }
}
