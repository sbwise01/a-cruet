package com.bradandmarsha.acruet.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
}
