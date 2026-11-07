package com.bradandmarsha.acruet.position;

import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.household.HouseholdRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for household position groups.
 */
public final class PositionGroupRepository implements PositionGroupStore {

    private static final String GROUP_SELECT = """
            SELECT id, household_id, encrypted_name, status, created_at, updated_at, archived_at
            FROM financial_position_group
            """;

    private final HouseholdRepository householdRepository = new HouseholdRepository();

    @Override
    public List<PositionGroup> listByHousehold(UUID householdId, boolean includeArchived) {
        return inTransaction(connection -> listByHousehold(connection, householdId, includeArchived));
    }

    @Override
    public Optional<PositionGroup> findById(UUID householdId, UUID groupId) {
        return inTransaction(connection -> findById(connection, householdId, groupId));
    }

    @Override
    public int countActive(UUID householdId) {
        return inTransaction(connection -> countActive(connection, householdId));
    }

    @Override
    public int countActivePositions(UUID householdId, UUID groupId) {
        return inTransaction(connection -> countActivePositions(connection, householdId, groupId));
    }

    @Override
    public void insert(PositionGroup group) {
        inTransaction(connection -> {
            insert(connection, group);
            householdRepository.incrementPositionGroupCount(connection, group.householdId());
            return null;
        });
    }

    @Override
    public void updateEncryptedName(UUID householdId, UUID groupId, byte[] encryptedName) {
        inTransaction(connection -> {
            updateEncryptedName(connection, householdId, groupId, encryptedName);
            return null;
        });
    }

    @Override
    public void archive(UUID householdId, UUID groupId) {
        inTransaction(connection -> {
            archive(connection, householdId, groupId);
            householdRepository.decrementPositionGroupCount(connection, householdId);
            return null;
        });
    }

    @Override
    public void deleteAllForHousehold(UUID householdId) {
        inTransaction(connection -> {
            deleteAllForHousehold(connection, householdId);
            return null;
        });
    }

    List<PositionGroup> listByHousehold(Connection connection, UUID householdId, boolean includeArchived)
            throws SQLException {
        String sql = includeArchived
                ? GROUP_SELECT + """
                WHERE household_id = ?
                ORDER BY created_at ASC
                """
                : GROUP_SELECT + """
                WHERE household_id = ? AND status = 'ACTIVE'
                ORDER BY created_at ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PositionGroup> groups = new ArrayList<>();
                while (resultSet.next()) {
                    groups.add(mapRow(resultSet));
                }
                return groups;
            }
        }
    }

    Optional<PositionGroup> findById(Connection connection, UUID householdId, UUID groupId) throws SQLException {
        String sql = GROUP_SELECT + " WHERE id = ? AND household_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, groupId);
            statement.setObject(2, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    int countActive(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM financial_position_group
                WHERE household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    int countActivePositions(Connection connection, UUID householdId, UUID groupId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM financial_position
                WHERE household_id = ? AND group_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.setObject(2, groupId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    void insert(Connection connection, PositionGroup group) throws SQLException {
        String sql = """
                INSERT INTO financial_position_group (id, household_id, encrypted_name, status)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, group.id());
            statement.setObject(2, group.householdId());
            statement.setBytes(3, group.encryptedName());
            statement.setString(4, group.status().dbValue());
            statement.executeUpdate();
        }
    }

    void updateEncryptedName(Connection connection, UUID householdId, UUID groupId, byte[] encryptedName)
            throws SQLException {
        String sql = """
                UPDATE financial_position_group
                SET encrypted_name = ?, updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, encryptedName);
            statement.setObject(2, groupId);
            statement.setObject(3, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active group not found");
            }
        }
    }

    void archive(Connection connection, UUID householdId, UUID groupId) throws SQLException {
        String sql = """
                UPDATE financial_position_group
                SET status = 'ARCHIVED', archived_at = NOW(), updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, groupId);
            statement.setObject(2, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active group not found");
            }
        }
    }

    void deleteAllForHousehold(Connection connection, UUID householdId) throws SQLException {
        String sql = "DELETE FROM financial_position_group WHERE household_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.executeUpdate();
        }
    }

    private static PositionGroup mapRow(ResultSet resultSet) throws SQLException {
        Timestamp archivedAt = resultSet.getTimestamp("archived_at");
        return new PositionGroup(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("household_id", UUID.class),
                PositionStatus.fromDb(resultSet.getString("status")),
                resultSet.getBytes("encrypted_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant());
    }

    private static <T> T inTransaction(SqlWork<T> work) {
        try {
            return Database.inTransactionReturning(work::apply);
        } catch (SQLException exception) {
            throw new PositionException("Position group persistence failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
