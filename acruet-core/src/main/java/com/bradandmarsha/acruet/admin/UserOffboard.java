package com.bradandmarsha.acruet.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Active or historical offboard workflow for a provisioned user (Phase 11).
 */
public record UserOffboard(
        UUID userId,
        Instant initiatedAt,
        Instant exportDeadline,
        Instant exportCompletedAt,
        Instant purgedAt,
        String initiatedByKeycloakUserId,
        String initiatedByEmail) {

    public boolean isActive() {
        return purgedAt == null;
    }

    public boolean isExportComplete() {
        return exportCompletedAt != null;
    }

    public boolean isDueForPurge(Instant now) {
        return purgedAt == null && (exportCompletedAt != null || !exportDeadline.isAfter(now));
    }
}
