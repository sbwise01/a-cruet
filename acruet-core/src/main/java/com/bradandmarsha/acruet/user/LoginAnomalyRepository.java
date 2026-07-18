package com.bradandmarsha.acruet.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Persists unlinked Keycloak login events for admin follow-up (Phase 9 item 10).
 */
public final class LoginAnomalyRepository {

    public void insert(Connection connection, String keycloakUserId, String email, String detail)
            throws SQLException {
        String sql = """
                INSERT INTO login_anomaly (keycloak_user_id, email, detail)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keycloakUserId);
            statement.setString(2, email);
            statement.setString(3, detail);
            statement.executeUpdate();
        }
    }
}
