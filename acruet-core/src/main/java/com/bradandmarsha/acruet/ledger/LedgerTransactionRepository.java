package com.bradandmarsha.acruet.ledger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC persistence for append-only ledger transactions (Phase 8).
 */
public final class LedgerTransactionRepository {

    public void insert(
            Connection connection,
            UUID id,
            UUID userId,
            TransactionType transactionType,
            LocalDate transactionDate,
            byte[] encryptedPayload,
            List<UUID> accountIds) throws SQLException {
        String sql = """
                INSERT INTO ledger_transaction (
                    id, user_id, transaction_type, transaction_date, encrypted_payload
                ) VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setString(3, transactionType.dbValue());
            statement.setObject(4, transactionDate);
            statement.setBytes(5, encryptedPayload);
            statement.executeUpdate();
        }
        insertAccountLinks(connection, id, accountIds);
    }

    public Optional<LedgerTransaction> findById(Connection connection, UUID userId, UUID transactionId)
            throws SQLException {
        String sql = """
                SELECT id, user_id, transaction_type, transaction_date, encrypted_payload, created_at
                FROM ledger_transaction
                WHERE id = ? AND user_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, transactionId);
            statement.setObject(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                UUID id = resultSet.getObject("id", UUID.class);
                List<UUID> accountIds = loadAccountIds(connection, id);
                return Optional.of(mapRow(resultSet, accountIds));
            }
        }
    }

    public List<LedgerTransaction> listByUser(
            Connection connection,
            UUID userId,
            LocalDate fromDate,
            LocalDate toDate,
            UUID accountId) throws SQLException {
        String sql;
        if (accountId != null) {
            sql = """
                    SELECT t.id, t.user_id, t.transaction_type, t.transaction_date,
                           t.encrypted_payload, t.created_at
                    FROM ledger_transaction t
                    INNER JOIN ledger_transaction_account ta ON ta.transaction_id = t.id
                    WHERE t.user_id = ?
                      AND t.transaction_date >= ?
                      AND t.transaction_date <= ?
                      AND ta.account_id = ?
                    ORDER BY t.transaction_date DESC, t.created_at DESC
                    """;
        } else {
            sql = """
                    SELECT id, user_id, transaction_type, transaction_date, encrypted_payload, created_at
                    FROM ledger_transaction
                    WHERE user_id = ?
                      AND transaction_date >= ?
                      AND transaction_date <= ?
                    ORDER BY transaction_date DESC, created_at DESC
                    """;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setObject(2, fromDate);
            statement.setObject(3, toDate);
            if (accountId != null) {
                statement.setObject(4, accountId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LedgerTransaction> transactions = new ArrayList<>();
                while (resultSet.next()) {
                    UUID id = resultSet.getObject("id", UUID.class);
                    List<UUID> accountIds = loadAccountIds(connection, id);
                    transactions.add(mapRow(resultSet, accountIds));
                }
                return transactions;
            }
        }
    }

    public int countWriteAttempts(Connection connection, UUID userId, java.time.Instant since)
            throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM ledger_write_attempt
                WHERE user_id = ? AND attempted_at >= ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.setTimestamp(2, java.sql.Timestamp.from(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void recordWriteAttempt(Connection connection, UUID userId) throws SQLException {
        String sql = "INSERT INTO ledger_write_attempt (user_id) VALUES (?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    private static void insertAccountLinks(Connection connection, UUID transactionId, List<UUID> accountIds)
            throws SQLException {
        Set<UUID> uniqueIds = new LinkedHashSet<>(accountIds);
        String sql = """
                INSERT INTO ledger_transaction_account (transaction_id, account_id)
                VALUES (?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (UUID accountId : uniqueIds) {
                statement.setObject(1, transactionId);
                statement.setObject(2, accountId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<UUID> loadAccountIds(Connection connection, UUID transactionId) throws SQLException {
        String sql = """
                SELECT account_id FROM ledger_transaction_account
                WHERE transaction_id = ?
                ORDER BY account_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<UUID> accountIds = new ArrayList<>();
                while (resultSet.next()) {
                    accountIds.add(resultSet.getObject("account_id", UUID.class));
                }
                return accountIds;
            }
        }
    }

    private static LedgerTransaction mapRow(ResultSet resultSet, List<UUID> accountIds) throws SQLException {
        return new LedgerTransaction(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                TransactionType.fromDb(resultSet.getString("transaction_type")),
                resultSet.getObject("transaction_date", LocalDate.class),
                resultSet.getBytes("encrypted_payload"),
                resultSet.getTimestamp("created_at").toInstant(),
                accountIds);
    }
}
