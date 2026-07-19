package com.bradandmarsha.acruet.ledger;

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
 * JDBC persistence for ledger accounts (Phase 8, household-scoped Phase 12).
 */
public final class LedgerAccountRepository {

    public void insert(Connection connection, UUID id, UUID householdId, byte[] encryptedName) throws SQLException {
        String sql = """
                INSERT INTO ledger_account (id, household_id, status, encrypted_name)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, householdId);
            statement.setString(3, LedgerAccountStatus.ACTIVE.dbValue());
            statement.setBytes(4, encryptedName);
            statement.executeUpdate();
        }
    }

    public Optional<LedgerAccount> findById(Connection connection, UUID householdId, UUID accountId)
            throws SQLException {
        String sql = """
                SELECT id, household_id, status, encrypted_name, created_at, updated_at, archived_at
                FROM ledger_account
                WHERE id = ? AND household_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setObject(2, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public List<LedgerAccount> listByHousehold(
            Connection connection,
            UUID householdId,
            boolean includeArchived) throws SQLException {
        String sql = includeArchived
                ? """
                SELECT id, household_id, status, encrypted_name, created_at, updated_at, archived_at
                FROM ledger_account
                WHERE household_id = ?
                ORDER BY created_at ASC
                """
                : """
                SELECT id, household_id, status, encrypted_name, created_at, updated_at, archived_at
                FROM ledger_account
                WHERE household_id = ? AND status = 'ACTIVE'
                ORDER BY created_at ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LedgerAccount> accounts = new ArrayList<>();
                while (resultSet.next()) {
                    accounts.add(mapRow(resultSet));
                }
                return accounts;
            }
        }
    }

    public void updateEncryptedName(Connection connection, UUID householdId, UUID accountId, byte[] encryptedName)
            throws SQLException {
        String sql = """
                UPDATE ledger_account
                SET encrypted_name = ?, updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, encryptedName);
            statement.setObject(2, accountId);
            statement.setObject(3, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active account not found");
            }
        }
    }

    public void archive(Connection connection, UUID householdId, UUID accountId) throws SQLException {
        String sql = """
                UPDATE ledger_account
                SET status = 'ARCHIVED', archived_at = NOW(), updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setObject(2, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active account not found");
            }
        }
    }

    public boolean allBelongToHousehold(Connection connection, UUID householdId, List<UUID> accountIds)
            throws SQLException {
        if (accountIds.isEmpty()) {
            return false;
        }
        String placeholders = String.join(", ", accountIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT COUNT(*) FROM ledger_account
                WHERE household_id = ? AND id IN (%s)
                """.formatted(placeholders);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            for (int index = 0; index < accountIds.size(); index += 1) {
                statement.setObject(index + 2, accountIds.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == accountIds.size();
            }
        }
    }

    private static LedgerAccount mapRow(ResultSet resultSet) throws SQLException {
        Timestamp archivedAt = resultSet.getTimestamp("archived_at");
        return new LedgerAccount(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("household_id", UUID.class),
                LedgerAccountStatus.fromDb(resultSet.getString("status")),
                resultSet.getBytes("encrypted_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant());
    }
}
