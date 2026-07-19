package com.bradandmarsha.acruet.signup;

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
import java.util.UUID;

/**
 * JDBC persistence for signup applications and rate-limit attempts.
 */
public final class SignupRepository {

    public Optional<SignupApplication> findById(UUID id) throws SQLException {
        String sql = """
                SELECT id, email, full_name, reason, phone, mailing_address, status,
                       rejection_count, last_rejected_at, household_invite_id, created_at
                FROM signup_application
                WHERE id = ?
                """;
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapApplication(resultSet));
            }
        }
    }

    public List<PendingApplication> listPendingApproval() throws SQLException {
        String sql = """
                SELECT id, email, full_name, reason, phone, mailing_address, verified_at,
                       created_at, household_invite_id
                FROM signup_application
                WHERE status = ?
                ORDER BY verified_at ASC NULLS LAST, created_at ASC
                """;
        List<PendingApplication> pending = new ArrayList<>();
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ApplicationStatus.PENDING_APPROVAL.dbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp verifiedAt = resultSet.getTimestamp("verified_at");
                    pending.add(new PendingApplication(
                            resultSet.getObject("id", UUID.class),
                            resultSet.getString("email"),
                            resultSet.getString("full_name"),
                            resultSet.getString("reason"),
                            resultSet.getString("phone"),
                            resultSet.getString("mailing_address"),
                            Optional.ofNullable(verifiedAt).map(Timestamp::toInstant),
                            resultSet.getTimestamp("created_at").toInstant(),
                            Optional.ofNullable(resultSet.getObject("household_invite_id", UUID.class))));
                }
            }
        }
        return pending;
    }

    public boolean markApproved(Connection connection, UUID id) throws SQLException {
        String sql = """
                UPDATE signup_application
                SET status = ?, updated_at = NOW()
                WHERE id = ? AND status = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ApplicationStatus.APPROVED.dbValue());
            statement.setObject(2, id);
            statement.setString(3, ApplicationStatus.PENDING_APPROVAL.dbValue());
            return statement.executeUpdate() == 1;
        }
    }

    public RejectionResult markRejected(Connection connection, UUID id, Instant rejectedAt)
            throws SQLException {
        String sql = """
                UPDATE signup_application
                SET rejection_count = rejection_count + 1,
                    last_rejected_at = ?,
                    status = CASE
                        WHEN rejection_count + 1 >= ? THEN ?
                        ELSE ?
                    END,
                    updated_at = NOW()
                WHERE id = ? AND status = ?
                RETURNING rejection_count, status
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(rejectedAt));
            statement.setInt(2, SignupPolicy.MAX_REJECTIONS);
            statement.setString(3, ApplicationStatus.BLOCKED.dbValue());
            statement.setString(4, ApplicationStatus.REJECTED.dbValue());
            statement.setObject(5, id);
            statement.setString(6, ApplicationStatus.PENDING_APPROVAL.dbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new RejectionResult(
                        resultSet.getInt("rejection_count"),
                        ApplicationStatus.fromDb(resultSet.getString("status")));
            }
        }
    }

    public record PendingApplication(
            UUID id,
            String email,
            String fullName,
            String reason,
            String phone,
            String mailingAddress,
            Optional<Instant> verifiedAt,
            Instant createdAt,
            Optional<UUID> householdInviteId) {
    }

    public record RejectionResult(int rejectionCount, ApplicationStatus status) {
    }

    public List<SignupApplication> listBlocked() throws SQLException {
        String sql = """
                SELECT id, email, full_name, reason, phone, mailing_address, status,
                       rejection_count, last_rejected_at, household_invite_id, created_at
                FROM signup_application
                WHERE status = ?
                ORDER BY created_at DESC
                """;
        List<SignupApplication> blocked = new ArrayList<>();
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ApplicationStatus.BLOCKED.dbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    blocked.add(mapApplication(resultSet));
                }
            }
        }
        return blocked;
    }

    public boolean unblock(Connection connection, UUID id) throws SQLException {
        String sql = """
                UPDATE signup_application
                SET status = ?, rejection_count = 0, last_rejected_at = NULL, updated_at = NOW()
                WHERE id = ? AND status = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ApplicationStatus.REJECTED.dbValue());
            statement.setObject(2, id);
            statement.setString(3, ApplicationStatus.BLOCKED.dbValue());
            return statement.executeUpdate() == 1;
        }
    }

    public Optional<SignupApplication> findLatestByEmail(String email) throws SQLException {
        String sql = """
                SELECT id, email, full_name, reason, phone, mailing_address, status,
                       rejection_count, last_rejected_at, household_invite_id, created_at
                FROM signup_application
                WHERE LOWER(email) = LOWER(?)
                ORDER BY created_at DESC
                LIMIT 1
                """;
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapApplication(resultSet));
            }
        }
    }

    public void insertApplication(
            UUID id,
            String email,
            String fullName,
            String reason,
            String phone,
            String mailingAddress,
            String tokenHash,
            Instant tokenExpiresAt,
            String applicantIp,
            UUID householdInviteId) throws SQLException {
        String sql = """
                INSERT INTO signup_application (
                    id, email, full_name, reason, phone, mailing_address, status,
                    verification_token_hash, verification_token_expires_at, applicant_ip,
                    household_invite_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setString(2, email.trim());
            statement.setString(3, fullName.trim());
            statement.setString(4, reason.trim());
            statement.setString(5, phone.trim());
            statement.setString(6, mailingAddress.trim());
            statement.setString(7, ApplicationStatus.PENDING_VERIFICATION.dbValue());
            statement.setString(8, tokenHash);
            statement.setTimestamp(9, Timestamp.from(tokenExpiresAt));
            statement.setString(10, applicantIp);
            statement.setObject(11, householdInviteId);
            statement.executeUpdate();
        }
    }

    public void deleteApplication(UUID id) throws SQLException {
        String sql = "DELETE FROM signup_application WHERE id = ?";
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    public boolean hasHouseholdInviteApplication(Connection connection, UUID userId) throws SQLException {
        String sql = """
                SELECT 1
                FROM acruet_user u
                INNER JOIN signup_application sa ON sa.id = u.signup_application_id
                WHERE u.id = ? AND sa.household_invite_id IS NOT NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public boolean verifyByTokenHash(String tokenHash, Instant now) throws SQLException {
        String sql = """
                UPDATE signup_application
                SET status = ?, verified_at = ?, verification_token_hash = NULL,
                    verification_token_expires_at = NULL, updated_at = NOW()
                WHERE verification_token_hash = ?
                  AND verification_token_expires_at > ?
                  AND status = ?
                """;
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ApplicationStatus.PENDING_APPROVAL.dbValue());
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, tokenHash);
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setString(5, ApplicationStatus.PENDING_VERIFICATION.dbValue());
            return statement.executeUpdate() == 1;
        }
    }

    public int countIpAttempts(String ipAddress, Instant since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM signup_attempt WHERE ip_address = ? AND attempted_at >= ?";
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ipAddress);
            statement.setTimestamp(2, Timestamp.from(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public int countEmailAttempts(String email, Instant since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM signup_attempt WHERE LOWER(email) = LOWER(?) AND attempted_at >= ?";
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setTimestamp(2, Timestamp.from(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void recordAttempt(String email, String ipAddress) throws SQLException {
        String sql = "INSERT INTO signup_attempt (email, ip_address) VALUES (?, ?)";
        try (Connection connection = Database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email == null ? null : email.trim());
            statement.setString(2, ipAddress);
            statement.executeUpdate();
        }
    }

    private SignupApplication mapApplication(ResultSet resultSet) throws SQLException {
        Timestamp lastRejected = resultSet.getTimestamp("last_rejected_at");
        return new SignupApplication(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("full_name"),
                resultSet.getString("reason"),
                resultSet.getString("phone"),
                resultSet.getString("mailing_address"),
                ApplicationStatus.fromDb(resultSet.getString("status")),
                resultSet.getInt("rejection_count"),
                Optional.ofNullable(lastRejected).map(Timestamp::toInstant),
                Optional.ofNullable(resultSet.getObject("household_invite_id", UUID.class)),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
