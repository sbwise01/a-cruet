package com.bradandmarsha.acruet.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for provisioned a-cruet users.
 */
public final class UserRepository {

    public void insert(
            Connection connection,
            UUID id,
            String keycloakUserId,
            String email,
            String displayName,
            UUID signupApplicationId) throws SQLException {
        String sql = """
                INSERT INTO acruet_user (
                    id, keycloak_user_id, email, display_name, signup_application_id
                ) VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setString(2, keycloakUserId);
            statement.setString(3, email.trim());
            statement.setString(4, displayName.trim());
            statement.setObject(5, signupApplicationId);
            statement.executeUpdate();
        }
    }

    public Optional<AcruetUser> findByKeycloakUserId(Connection connection, String keycloakUserId)
            throws SQLException {
        String sql = """
                SELECT id, keycloak_user_id, email, display_name, signup_application_id,
                       ledger_account_count, transaction_count, key_setup_complete,
                       created_at, updated_at, last_login_at
                FROM acruet_user
                WHERE keycloak_user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keycloakUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public void updateLastLogin(Connection connection, UUID userId, Instant lastLoginAt)
            throws SQLException {
        String sql = """
                UPDATE acruet_user
                SET last_login_at = ?, updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(lastLoginAt));
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    public void markKeySetupComplete(Connection connection, UUID userId) throws SQLException {
        String sql = """
                UPDATE acruet_user
                SET key_setup_complete = TRUE, updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    private static AcruetUser mapRow(ResultSet resultSet) throws SQLException {
        Timestamp lastLoginAt = resultSet.getTimestamp("last_login_at");
        return new AcruetUser(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("keycloak_user_id"),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getObject("signup_application_id", UUID.class),
                resultSet.getInt("ledger_account_count"),
                resultSet.getInt("transaction_count"),
                resultSet.getBoolean("key_setup_complete"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                lastLoginAt == null ? null : lastLoginAt.toInstant());
    }
}
