package com.bradandmarsha.acruet.keys;

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
 * JDBC persistence for WebAuthn device wraps.
 */
public final class UserUnlockDeviceRepository implements UnlockDeviceStore {

    @Override
    public List<UserUnlockDevice> listByUser(UUID userId) {
        return inTransaction(connection -> listByUser(connection, userId));
    }

    @Override
    public int countByUser(UUID userId) {
        return inTransaction(connection -> countByUser(connection, userId));
    }

    @Override
    public Optional<UserUnlockDevice> findById(UUID userId, UUID id) {
        return inTransaction(connection -> findById(connection, userId, id));
    }

    @Override
    public Optional<UserUnlockDevice> findByCredentialId(byte[] credentialId) {
        return inTransaction(connection -> findByCredentialId(connection, credentialId));
    }

    @Override
    public void insert(UserUnlockDevice device) {
        inTransaction(connection -> {
            insert(connection, device);
            return null;
        });
    }

    @Override
    public boolean updateDisplayName(UUID userId, UUID id, String displayName) {
        return inTransaction(connection -> updateDisplayName(connection, userId, id, displayName));
    }

    @Override
    public boolean delete(UUID userId, UUID id) {
        return inTransaction(connection -> delete(connection, userId, id));
    }

    @Override
    public void touchLastUsed(UUID userId, UUID id, Instant at) {
        inTransaction(connection -> {
            touchLastUsed(connection, userId, id, at);
            return null;
        });
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        inTransaction(connection -> {
            deleteAllForUser(connection, userId);
            return null;
        });
    }

    List<UserUnlockDevice> listByUser(Connection connection, UUID userId) throws SQLException {
        String sql = """
                SELECT id, user_id, credential_id, wrapped_dek, wrap_algorithm, display_name,
                       created_at, last_used_at
                FROM user_unlock_device
                WHERE user_id = ?
                ORDER BY created_at ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<UserUnlockDevice> devices = new ArrayList<>();
                while (resultSet.next()) {
                    devices.add(mapRow(resultSet));
                }
                return devices;
            }
        }
    }

    int countByUser(Connection connection, UUID userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_unlock_device WHERE user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    Optional<UserUnlockDevice> findById(Connection connection, UUID userId, UUID id) throws SQLException {
        String sql = """
                SELECT id, user_id, credential_id, wrapped_dek, wrap_algorithm, display_name,
                       created_at, last_used_at
                FROM user_unlock_device
                WHERE user_id = ? AND id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setObject(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    Optional<UserUnlockDevice> findByCredentialId(Connection connection, byte[] credentialId) throws SQLException {
        String sql = """
                SELECT id, user_id, credential_id, wrapped_dek, wrap_algorithm, display_name,
                       created_at, last_used_at
                FROM user_unlock_device
                WHERE credential_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, credentialId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    void insert(Connection connection, UserUnlockDevice device) throws SQLException {
        String sql = """
                INSERT INTO user_unlock_device (
                    id, user_id, credential_id, wrapped_dek, wrap_algorithm, display_name
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, device.id());
            statement.setObject(2, device.userId());
            statement.setBytes(3, device.credentialId());
            statement.setBytes(4, device.wrappedDek());
            statement.setString(5, device.wrapAlgorithm());
            statement.setString(6, device.displayName());
            statement.executeUpdate();
        }
    }

    boolean updateDisplayName(Connection connection, UUID userId, UUID id, String displayName) throws SQLException {
        String sql = """
                UPDATE user_unlock_device
                SET display_name = ?
                WHERE user_id = ? AND id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            statement.setObject(2, userId);
            statement.setObject(3, id);
            return statement.executeUpdate() == 1;
        }
    }

    boolean delete(Connection connection, UUID userId, UUID id) throws SQLException {
        String sql = "DELETE FROM user_unlock_device WHERE user_id = ? AND id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setObject(2, id);
            return statement.executeUpdate() == 1;
        }
    }

    void touchLastUsed(Connection connection, UUID userId, UUID id, Instant at) throws SQLException {
        String sql = """
                UPDATE user_unlock_device
                SET last_used_at = ?
                WHERE user_id = ? AND id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(at));
            statement.setObject(2, userId);
            statement.setObject(3, id);
            statement.executeUpdate();
        }
    }

    void deleteAllForUser(Connection connection, UUID userId) throws SQLException {
        String sql = "DELETE FROM user_unlock_device WHERE user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    private static UserUnlockDevice mapRow(ResultSet resultSet) throws SQLException {
        return new UserUnlockDevice(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getBytes("credential_id"),
                resultSet.getBytes("wrapped_dek"),
                resultSet.getString("wrap_algorithm"),
                resultSet.getString("display_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("last_used_at").toInstant());
    }

    private static <T> T inTransaction(SqlWork<T> work) {
        try {
            return Database.inTransactionReturning(work::apply);
        } catch (SQLException exception) {
            throw new DeviceUnlockException("Device store failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
