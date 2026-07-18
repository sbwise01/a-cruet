package com.bradandmarsha.acruet.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for wrapped DEK blobs (Phase 7).
 */
public final class UserEncryptionRepository {

    public void insert(Connection connection, UserEncryptionKey key) throws SQLException {
        String sql = """
                INSERT INTO user_encryption_key (
                    user_id, wrapped_dek, wrap_algorithm, kdf_algorithm, kdf_hash,
                    kdf_salt, kdf_iterations, recovery_wrapped_dek, recovery_wrap_algorithm
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, key);
            statement.executeUpdate();
        }
    }

    public void updatePassphraseWrap(Connection connection, UserEncryptionKey key) throws SQLException {
        String sql = """
                UPDATE user_encryption_key
                SET wrapped_dek = ?, wrap_algorithm = ?, kdf_algorithm = ?, kdf_hash = ?,
                    kdf_salt = ?, kdf_iterations = ?, updated_at = NOW()
                WHERE user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, key.wrappedDek());
            statement.setString(2, key.wrapAlgorithm());
            statement.setString(3, key.kdfAlgorithm());
            statement.setString(4, key.kdfHash());
            statement.setBytes(5, key.kdfSalt());
            statement.setInt(6, key.kdfIterations());
            statement.setObject(7, key.userId());
            statement.executeUpdate();
        }
    }

    public void updateDualWrap(Connection connection, UserEncryptionKey key) throws SQLException {
        String sql = """
                UPDATE user_encryption_key
                SET wrapped_dek = ?, wrap_algorithm = ?, kdf_algorithm = ?, kdf_hash = ?,
                    kdf_salt = ?, kdf_iterations = ?,
                    recovery_wrapped_dek = ?, recovery_wrap_algorithm = ?,
                    updated_at = NOW()
                WHERE user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, key.wrappedDek());
            statement.setString(2, key.wrapAlgorithm());
            statement.setString(3, key.kdfAlgorithm());
            statement.setString(4, key.kdfHash());
            statement.setBytes(5, key.kdfSalt());
            statement.setInt(6, key.kdfIterations());
            statement.setBytes(7, key.recoveryWrappedDek());
            statement.setString(8, key.recoveryWrapAlgorithm());
            statement.setObject(9, key.userId());
            statement.executeUpdate();
        }
    }

    public void updateRecoveryWrap(Connection connection, UUID userId, byte[] recoveryWrappedDek, String algorithm)
            throws SQLException {
        String sql = """
                UPDATE user_encryption_key
                SET recovery_wrapped_dek = ?, recovery_wrap_algorithm = ?, updated_at = NOW()
                WHERE user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, recoveryWrappedDek);
            statement.setString(2, algorithm);
            statement.setObject(3, userId);
            statement.executeUpdate();
        }
    }

    public Optional<UserEncryptionKey> findByUserId(Connection connection, UUID userId) throws SQLException {
        String sql = """
                SELECT user_id, wrapped_dek, wrap_algorithm, kdf_algorithm, kdf_hash,
                       kdf_salt, kdf_iterations, recovery_wrapped_dek, recovery_wrap_algorithm,
                       created_at, updated_at
                FROM user_encryption_key
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

    public boolean existsForUser(Connection connection, UUID userId) throws SQLException {
        String sql = "SELECT 1 FROM user_encryption_key WHERE user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public boolean recoveryEnrolled(Connection connection, UUID userId) throws SQLException {
        String sql = """
                SELECT 1 FROM user_encryption_key
                WHERE user_id = ?
                  AND recovery_wrapped_dek IS NOT NULL
                  AND recovery_wrap_algorithm IS NOT NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static UserEncryptionKey mapRow(ResultSet resultSet) throws SQLException {
        return new UserEncryptionKey(
                resultSet.getObject("user_id", UUID.class),
                resultSet.getBytes("wrapped_dek"),
                resultSet.getString("wrap_algorithm"),
                resultSet.getString("kdf_algorithm"),
                resultSet.getString("kdf_hash"),
                resultSet.getBytes("kdf_salt"),
                resultSet.getInt("kdf_iterations"),
                resultSet.getBytes("recovery_wrapped_dek"),
                resultSet.getString("recovery_wrap_algorithm"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private static void bindKey(PreparedStatement statement, UserEncryptionKey key) throws SQLException {
        statement.setObject(1, key.userId());
        statement.setBytes(2, key.wrappedDek());
        statement.setString(3, key.wrapAlgorithm());
        statement.setString(4, key.kdfAlgorithm());
        statement.setString(5, key.kdfHash());
        statement.setBytes(6, key.kdfSalt());
        statement.setInt(7, key.kdfIterations());
        statement.setBytes(8, key.recoveryWrappedDek());
        statement.setString(9, key.recoveryWrapAlgorithm());
    }
}
