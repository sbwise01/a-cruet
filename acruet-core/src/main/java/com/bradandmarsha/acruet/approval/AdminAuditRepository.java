package com.bradandmarsha.acruet.approval;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Admin action audit trail (PRODUCT.md Section 6 #51).
 */
public final class AdminAuditRepository {

    public void insert(
            Connection connection,
            String adminKeycloakUserId,
            String adminEmail,
            ApprovalAction action,
            String targetType,
            UUID targetId,
            String detail) throws SQLException {
        String sql = """
                INSERT INTO admin_action_audit (
                    admin_keycloak_user_id, admin_email, action, target_type, target_id, detail
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, adminKeycloakUserId);
            statement.setString(2, adminEmail);
            statement.setString(3, action.dbValue());
            statement.setString(4, targetType);
            statement.setObject(5, targetId);
            statement.setString(6, detail);
            statement.executeUpdate();
        }
    }
}
