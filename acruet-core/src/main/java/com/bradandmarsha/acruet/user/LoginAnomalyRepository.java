package com.bradandmarsha.acruet.user;

import com.bradandmarsha.acruet.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists unlinked Keycloak login events for admin follow-up (Phase 9 item 10, Phase 11 alerts).
 */
public final class LoginAnomalyRepository {

    public long insertReturningId(Connection connection, String keycloakUserId, String email, String detail)
            throws SQLException {
        String sql = """
                INSERT INTO login_anomaly (keycloak_user_id, email, detail)
                VALUES (?, ?, ?)
                RETURNING id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keycloakUserId);
            statement.setString(2, email);
            statement.setString(3, detail);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("login_anomaly insert did not return id");
                }
                return resultSet.getLong("id");
            }
        }
    }

    public void insert(Connection connection, String keycloakUserId, String email, String detail)
            throws SQLException {
        insertReturningId(connection, keycloakUserId, email, detail);
    }

    public List<LoginAnomaly> listRecent(int limit) throws SQLException {
        String sql = """
                SELECT id, keycloak_user_id, email, detail, created_at, alerted_at
                FROM login_anomaly
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<LoginAnomaly> anomalies = new ArrayList<>();
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    anomalies.add(mapRow(resultSet));
                }
            }
        }
        return anomalies;
    }

    public List<LoginAnomaly> listUnalerted() throws SQLException {
        String sql = """
                SELECT id, keycloak_user_id, email, detail, created_at, alerted_at
                FROM login_anomaly
                WHERE alerted_at IS NULL
                ORDER BY created_at ASC
                """;
        List<LoginAnomaly> anomalies = new ArrayList<>();
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                anomalies.add(mapRow(resultSet));
            }
        }
        return anomalies;
    }

    public boolean markAlerted(Connection connection, long id, Instant alertedAt) throws SQLException {
        String sql = """
                UPDATE login_anomaly
                SET alerted_at = ?
                WHERE id = ? AND alerted_at IS NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(alertedAt));
            statement.setLong(2, id);
            return statement.executeUpdate() == 1;
        }
    }

    public Optional<LoginAnomaly> findById(long id) throws SQLException {
        String sql = """
                SELECT id, keycloak_user_id, email, detail, created_at, alerted_at
                FROM login_anomaly
                WHERE id = ?
                """;
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    private static LoginAnomaly mapRow(ResultSet resultSet) throws SQLException {
        Timestamp alertedAt = resultSet.getTimestamp("alerted_at");
        return new LoginAnomaly(
                resultSet.getLong("id"),
                resultSet.getString("keycloak_user_id"),
                resultSet.getString("email"),
                resultSet.getString("detail"),
                resultSet.getTimestamp("created_at").toInstant(),
                alertedAt == null ? null : alertedAt.toInstant());
    }

    public record LoginAnomaly(
            long id,
            String keycloakUserId,
            String email,
            String detail,
            Instant createdAt,
            Instant alertedAt) {
    }
}
