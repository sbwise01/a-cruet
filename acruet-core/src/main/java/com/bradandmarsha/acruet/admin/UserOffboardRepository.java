package com.bradandmarsha.acruet.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Offboard export window and purge scheduling (Phase 11).
 */
public final class UserOffboardRepository {

    public void insert(
            Connection connection,
            UUID userId,
            Instant exportDeadline,
            String initiatedByKeycloakUserId,
            String initiatedByEmail)
            throws SQLException {
        String sql = """
                INSERT INTO user_offboard (
                    user_id, export_deadline, initiated_by_keycloak_user_id, initiated_by_email
                ) VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setTimestamp(2, Timestamp.from(exportDeadline));
            statement.setString(3, initiatedByKeycloakUserId);
            statement.setString(4, initiatedByEmail);
            statement.executeUpdate();
        }
    }

    public Optional<UserOffboard> findByUserId(Connection connection, UUID userId) throws SQLException {
        String sql = """
                SELECT user_id, initiated_at, export_deadline, export_completed_at, purged_at,
                       initiated_by_keycloak_user_id, initiated_by_email
                FROM user_offboard
                WHERE user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public boolean markExportComplete(Connection connection, UUID userId, Instant completedAt)
            throws SQLException {
        String sql = """
                UPDATE user_offboard
                SET export_completed_at = ?
                WHERE user_id = ? AND purged_at IS NULL AND export_completed_at IS NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(completedAt));
            statement.setObject(2, userId);
            return statement.executeUpdate() == 1;
        }
    }

    public void markPurged(Connection connection, UUID userId, Instant purgedAt) throws SQLException {
        String sql = """
                UPDATE user_offboard
                SET purged_at = ?
                WHERE user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(purgedAt));
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    public List<UserOffboard> listDueForPurge(Instant now) throws SQLException {
        String sql = """
                SELECT user_id, initiated_at, export_deadline, export_completed_at, purged_at,
                       initiated_by_keycloak_user_id, initiated_by_email
                FROM user_offboard
                WHERE purged_at IS NULL
                  AND (export_completed_at IS NOT NULL OR export_deadline <= ?)
                ORDER BY export_deadline ASC
                """;
        List<UserOffboard> due = new ArrayList<>();
        try (Connection connection = com.bradandmarsha.acruet.db.Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    due.add(mapRow(resultSet));
                }
            }
        }
        return due;
    }

    private static UserOffboard mapRow(ResultSet resultSet) throws SQLException {
        Timestamp exportCompletedAt = resultSet.getTimestamp("export_completed_at");
        Timestamp purgedAt = resultSet.getTimestamp("purged_at");
        return new UserOffboard(
                resultSet.getObject("user_id", UUID.class),
                resultSet.getTimestamp("initiated_at").toInstant(),
                resultSet.getTimestamp("export_deadline").toInstant(),
                exportCompletedAt == null ? null : exportCompletedAt.toInstant(),
                purgedAt == null ? null : purgedAt.toInstant(),
                resultSet.getString("initiated_by_keycloak_user_id"),
                resultSet.getString("initiated_by_email"));
    }
}
