package com.bradandmarsha.acruet.admin;

import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.user.AcruetUser;

import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User-facing offboard export window (Phase 11).
 */
public final class OffboardService {

    private static final Logger LOGGER = Logger.getLogger(OffboardService.class.getName());

    private final UserOffboardRepository offboardRepository = new UserOffboardRepository();

    public Optional<OffboardStatus> status(AcruetUser user) {
        try {
            return Database.inTransactionReturning(
                    connection -> offboardRepository.findByUserId(connection, user.id()))
                    .filter(UserOffboard::isActive)
                    .map(offboard -> new OffboardStatus(
                            offboard.exportDeadline(),
                            offboard.exportCompletedAt(),
                            offboard.isExportComplete()));
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to load offboard status", exception);
            return Optional.empty();
        }
    }

    public boolean markExportComplete(AcruetUser user) {
        try {
            return Database.inTransactionReturning(connection -> offboardRepository.markExportComplete(
                    connection, user.id(), Instant.now()));
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING, "Failed to mark offboard export complete", exception);
            return false;
        }
    }

    public record OffboardStatus(Instant exportDeadline, Instant exportCompletedAt, boolean exportComplete) {
    }
}
