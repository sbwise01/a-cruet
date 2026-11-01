package com.bradandmarsha.acruet.networth;

import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.household.HouseholdRepository;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for household net-worth observations.
 */
public final class NetWorthSnapshotRepository implements NetWorthStore {

    private final HouseholdRepository householdRepository = new HouseholdRepository();

    @Override
    public List<NetWorthSnapshot> listByHousehold(UUID householdId) {
        return inTransaction(connection -> listByHousehold(connection, householdId));
    }

    @Override
    public Optional<NetWorthSnapshot> findLatest(UUID householdId) {
        return inTransaction(connection -> findLatest(connection, householdId));
    }

    @Override
    public int countByHousehold(UUID householdId) {
        return inTransaction(connection -> countByHousehold(connection, householdId));
    }

    @Override
    public void insert(NetWorthSnapshot snapshot) {
        inTransaction(connection -> {
            insert(connection, snapshot);
            householdRepository.incrementNetWorthSnapshotCount(connection, snapshot.householdId());
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

    List<NetWorthSnapshot> listByHousehold(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT id, household_id, as_of_date, encrypted_payload, created_at
                FROM net_worth_snapshot
                WHERE household_id = ?
                ORDER BY as_of_date ASC, created_at ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<NetWorthSnapshot> snapshots = new ArrayList<>();
                while (resultSet.next()) {
                    snapshots.add(mapRow(resultSet));
                }
                return snapshots;
            }
        }
    }

    Optional<NetWorthSnapshot> findLatest(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT id, household_id, as_of_date, encrypted_payload, created_at
                FROM net_worth_snapshot
                WHERE household_id = ?
                ORDER BY as_of_date DESC, created_at DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    int countByHousehold(Connection connection, UUID householdId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM net_worth_snapshot WHERE household_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    void insert(Connection connection, NetWorthSnapshot snapshot) throws SQLException {
        String sql = """
                INSERT INTO net_worth_snapshot (id, household_id, as_of_date, encrypted_payload, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, snapshot.id());
            statement.setObject(2, snapshot.householdId());
            statement.setDate(3, Date.valueOf(snapshot.asOfDate()));
            statement.setBytes(4, snapshot.encryptedPayload());
            statement.setTimestamp(5, java.sql.Timestamp.from(snapshot.createdAt()));
            statement.executeUpdate();
        }
    }

    void deleteAllForHousehold(Connection connection, UUID householdId) throws SQLException {
        String sql = "DELETE FROM net_worth_snapshot WHERE household_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.executeUpdate();
        }
    }

    private static NetWorthSnapshot mapRow(ResultSet resultSet) throws SQLException {
        return new NetWorthSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("household_id", UUID.class),
                resultSet.getDate("as_of_date").toLocalDate(),
                resultSet.getBytes("encrypted_payload"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static <T> T inTransaction(SqlWork<T> work) {
        try {
            return Database.inTransactionReturning(work::apply);
        } catch (SQLException exception) {
            throw new NetWorthException("Net worth store failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
