package com.bradandmarsha.acruet.household;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for household member invites (Phase 12c).
 */
public final class HouseholdInviteRepository {

    public void insert(
            Connection connection,
            UUID id,
            UUID householdId,
            String email,
            String tokenHash,
            UUID invitedByUserId,
            byte[] encryptedInvitePayload,
            String wrapAlgorithm,
            String kdfAlgorithm,
            String kdfHash,
            byte[] kdfSalt,
            int kdfIterations,
            Instant expiresAt) throws SQLException {
        String sql = """
                INSERT INTO household_invite (
                    id, household_id, email, token_hash, status, invited_by_user_id,
                    encrypted_invite_payload, wrap_algorithm, kdf_algorithm, kdf_hash,
                    kdf_salt, kdf_iterations, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, householdId);
            statement.setString(3, email.trim());
            statement.setString(4, tokenHash);
            statement.setString(5, HouseholdInviteStatus.PENDING.dbValue());
            statement.setObject(6, invitedByUserId);
            statement.setBytes(7, encryptedInvitePayload);
            statement.setString(8, wrapAlgorithm);
            statement.setString(9, kdfAlgorithm);
            statement.setString(10, kdfHash);
            statement.setBytes(11, kdfSalt);
            statement.setInt(12, kdfIterations);
            statement.setTimestamp(13, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    public Optional<HouseholdInvite> findByTokenHash(Connection connection, String tokenHash, Instant now)
            throws SQLException {
        String sql = """
                SELECT id, household_id, email, invited_by_user_id, encrypted_invite_payload,
                       wrap_algorithm, kdf_algorithm, kdf_hash, kdf_salt, kdf_iterations,
                       status, expires_at, created_at, updated_at
                FROM household_invite
                WHERE token_hash = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                HouseholdInvite invite = mapRow(resultSet);
                if (invite.status() == HouseholdInviteStatus.PENDING && invite.expiresAt().isBefore(now)) {
                    markExpired(connection, invite.id());
                    return Optional.empty();
                }
                return Optional.of(invite);
            }
        }
    }

    public Optional<HouseholdInvite> findById(Connection connection, UUID id) throws SQLException {
        String sql = """
                SELECT id, household_id, email, invited_by_user_id, encrypted_invite_payload,
                       wrap_algorithm, kdf_algorithm, kdf_hash, kdf_salt, kdf_iterations,
                       status, expires_at, created_at, updated_at
                FROM household_invite
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public boolean hasPendingInviteForEmail(Connection connection, UUID householdId, String email)
            throws SQLException {
        String sql = """
                SELECT 1 FROM household_invite
                WHERE household_id = ? AND LOWER(email) = LOWER(?)
                  AND status = ? AND expires_at > NOW()
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.setString(2, email.trim());
            statement.setString(3, HouseholdInviteStatus.PENDING.dbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public boolean markAccepted(Connection connection, UUID inviteId) throws SQLException {
        String sql = """
                UPDATE household_invite
                SET status = ?, updated_at = NOW()
                WHERE id = ? AND status = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, HouseholdInviteStatus.ACCEPTED.dbValue());
            statement.setObject(2, inviteId);
            statement.setString(3, HouseholdInviteStatus.PENDING.dbValue());
            return statement.executeUpdate() == 1;
        }
    }

    public Optional<HouseholdInvite> findAcceptedInviteForMemberJoin(
            Connection connection, UUID userId, String tokenHash) throws SQLException {
        String sql = """
                SELECT hi.id, hi.household_id, hi.email, hi.invited_by_user_id,
                       hi.encrypted_invite_payload, hi.wrap_algorithm, hi.kdf_algorithm,
                       hi.kdf_hash, hi.kdf_salt, hi.kdf_iterations, hi.status,
                       hi.expires_at, hi.created_at, hi.updated_at
                FROM household_invite hi
                INNER JOIN signup_application sa ON sa.household_invite_id = hi.id
                INNER JOIN acruet_user u ON u.signup_application_id = sa.id
                INNER JOIN household_member hm ON hm.user_id = u.id
                WHERE u.id = ?
                  AND hi.token_hash = ?
                  AND hi.status = ?
                  AND hm.role = ?
                  AND LOWER(hi.email) = LOWER(u.email)
                  AND hi.household_id = u.household_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setString(2, tokenHash);
            statement.setString(3, HouseholdInviteStatus.ACCEPTED.dbValue());
            statement.setString(4, HouseholdMemberRole.MEMBER.dbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    private void markExpired(Connection connection, UUID inviteId) throws SQLException {
        String sql = """
                UPDATE household_invite
                SET status = ?, updated_at = NOW()
                WHERE id = ? AND status = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, HouseholdInviteStatus.EXPIRED.dbValue());
            statement.setObject(2, inviteId);
            statement.setString(3, HouseholdInviteStatus.PENDING.dbValue());
            statement.executeUpdate();
        }
    }

    private static HouseholdInvite mapRow(ResultSet resultSet) throws SQLException {
        return new HouseholdInvite(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("household_id", UUID.class),
                resultSet.getString("email"),
                resultSet.getObject("invited_by_user_id", UUID.class),
                resultSet.getBytes("encrypted_invite_payload"),
                resultSet.getString("wrap_algorithm"),
                resultSet.getString("kdf_algorithm"),
                resultSet.getString("kdf_hash"),
                resultSet.getBytes("kdf_salt"),
                resultSet.getInt("kdf_iterations"),
                HouseholdInviteStatus.fromDb(resultSet.getString("status")),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
