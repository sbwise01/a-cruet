package com.bradandmarsha.acruet.household;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for households and membership (Phase 12).
 */
public final class HouseholdRepository {

    public void insertOwnerHousehold(Connection connection, UUID householdId) throws SQLException {
        String householdSql = """
                INSERT INTO household (id)
                VALUES (?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(householdSql)) {
            statement.setObject(1, householdId);
            statement.executeUpdate();
        }
    }

    public void insertMember(
            Connection connection,
            UUID householdId,
            UUID userId,
            HouseholdMemberRole role) throws SQLException {
        String sql = """
                INSERT INTO household_member (household_id, user_id, role)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.setObject(2, userId);
            statement.setString(3, role.dbValue());
            statement.executeUpdate();
        }
    }

    public Optional<Household> findById(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT id, ledger_account_count, transaction_count, ledger_account_limit,
                       created_at, updated_at
                FROM household
                WHERE id = ?
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

    public int countMembers(Connection connection, UUID householdId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM household_member WHERE household_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public int countPendingInvites(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM household_invite
                WHERE household_id = ? AND status = 'pending' AND expires_at > NOW()
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void incrementLedgerAccountCount(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                UPDATE household
                SET ledger_account_count = ledger_account_count + 1, updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.executeUpdate();
        }
    }

    public void decrementLedgerAccountCount(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                UPDATE household
                SET ledger_account_count = GREATEST(ledger_account_count - 1, 0), updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.executeUpdate();
        }
    }

    public void incrementTransactionCount(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                UPDATE household
                SET transaction_count = transaction_count + 1, updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.executeUpdate();
        }
    }

    public void deleteById(Connection connection, UUID householdId) throws SQLException {
        String sql = "DELETE FROM household WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            statement.executeUpdate();
        }
    }

    public Optional<HouseholdMemberRole> findMemberRole(Connection connection, UUID userId) throws SQLException {
        String sql = "SELECT role FROM household_member WHERE user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(HouseholdMemberRole.fromDb(resultSet.getString("role")));
            }
        }
    }

    private static Household mapRow(ResultSet resultSet) throws SQLException {
        return new Household(
                resultSet.getObject("id", UUID.class),
                resultSet.getInt("ledger_account_count"),
                resultSet.getInt("transaction_count"),
                resultSet.getInt("ledger_account_limit"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
