package com.bradandmarsha.acruet.ledger;

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
 * JDBC persistence for envelope bundles.
 */
public final class EnvelopeBundleRepository {

    public void insertDefault(Connection connection, UUID id, UUID householdId) throws SQLException {
        String sql = """
                INSERT INTO envelope_bundle (
                    id, household_id, status, is_default,
                    ledger_account_count, ledger_account_limit, transaction_count
                ) VALUES (?, ?, ?, TRUE, 0, 100, 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, householdId);
            statement.setString(3, EnvelopeBundleStatus.ACTIVE.dbValue());
            statement.executeUpdate();
        }
    }

    public void insert(
            Connection connection,
            UUID id,
            UUID householdId,
            byte[] encryptedName) throws SQLException {
        String sql = """
                INSERT INTO envelope_bundle (
                    id, household_id, status, encrypted_name, is_default,
                    ledger_account_count, ledger_account_limit, transaction_count
                ) VALUES (?, ?, ?, ?, FALSE, 0, 100, 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, householdId);
            statement.setString(3, EnvelopeBundleStatus.ACTIVE.dbValue());
            statement.setBytes(4, encryptedName);
            statement.executeUpdate();
        }
    }

    public Optional<EnvelopeBundle> findById(Connection connection, UUID householdId, UUID bundleId)
            throws SQLException {
        String sql = """
                SELECT id, household_id, status, encrypted_name, is_default,
                       ledger_account_count, ledger_account_limit, transaction_count,
                       created_at, updated_at, archived_at
                FROM envelope_bundle
                WHERE id = ? AND household_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bundleId);
            statement.setObject(2, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public Optional<EnvelopeBundle> findDefault(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT id, household_id, status, encrypted_name, is_default,
                       ledger_account_count, ledger_account_limit, transaction_count,
                       created_at, updated_at, archived_at
                FROM envelope_bundle
                WHERE household_id = ? AND is_default
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

    public List<EnvelopeBundle> listByHousehold(
            Connection connection,
            UUID householdId,
            boolean includeArchived) throws SQLException {
        String sql = includeArchived
                ? """
                SELECT id, household_id, status, encrypted_name, is_default,
                       ledger_account_count, ledger_account_limit, transaction_count,
                       created_at, updated_at, archived_at
                FROM envelope_bundle
                WHERE household_id = ?
                ORDER BY is_default DESC, created_at ASC
                """
                : """
                SELECT id, household_id, status, encrypted_name, is_default,
                       ledger_account_count, ledger_account_limit, transaction_count,
                       created_at, updated_at, archived_at
                FROM envelope_bundle
                WHERE household_id = ? AND status = 'ACTIVE'
                ORDER BY is_default DESC, created_at ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<EnvelopeBundle> bundles = new ArrayList<>();
                while (resultSet.next()) {
                    bundles.add(mapRow(resultSet));
                }
                return bundles;
            }
        }
    }

    public int countActive(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM envelope_bundle
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

    public int countActiveAccounts(Connection connection, UUID bundleId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM ledger_account
                WHERE bundle_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bundleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void updateEncryptedName(
            Connection connection,
            UUID householdId,
            UUID bundleId,
            byte[] encryptedName) throws SQLException {
        String sql = """
                UPDATE envelope_bundle
                SET encrypted_name = ?, updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, encryptedName);
            statement.setObject(2, bundleId);
            statement.setObject(3, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active bundle not found");
            }
        }
    }

    public void archive(Connection connection, UUID householdId, UUID bundleId) throws SQLException {
        String sql = """
                UPDATE envelope_bundle
                SET status = 'ARCHIVED', archived_at = NOW(), updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bundleId);
            statement.setObject(2, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active bundle not found");
            }
        }
    }

    public void incrementLedgerAccountCount(Connection connection, UUID bundleId) throws SQLException {
        String sql = """
                UPDATE envelope_bundle
                SET ledger_account_count = ledger_account_count + 1, updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bundleId);
            statement.executeUpdate();
        }
    }

    public void decrementLedgerAccountCount(Connection connection, UUID bundleId) throws SQLException {
        String sql = """
                UPDATE envelope_bundle
                SET ledger_account_count = GREATEST(ledger_account_count - 1, 0), updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bundleId);
            statement.executeUpdate();
        }
    }

    public void incrementTransactionCount(Connection connection, UUID bundleId) throws SQLException {
        String sql = """
                UPDATE envelope_bundle
                SET transaction_count = transaction_count + 1, updated_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bundleId);
            statement.executeUpdate();
        }
    }

    private static EnvelopeBundle mapRow(ResultSet resultSet) throws SQLException {
        Timestamp archivedAt = resultSet.getTimestamp("archived_at");
        return new EnvelopeBundle(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("household_id", UUID.class),
                EnvelopeBundleStatus.fromDb(resultSet.getString("status")),
                resultSet.getBytes("encrypted_name"),
                resultSet.getBoolean("is_default"),
                resultSet.getInt("ledger_account_count"),
                resultSet.getInt("ledger_account_limit"),
                resultSet.getInt("transaction_count"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant());
    }
}
