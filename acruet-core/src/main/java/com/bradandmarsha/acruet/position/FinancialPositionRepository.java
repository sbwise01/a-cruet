package com.bradandmarsha.acruet.position;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC persistence for snapshot positions and valuations.
 */
public final class FinancialPositionRepository {

    private static final String POSITION_SELECT = """
            SELECT p.id, p.household_id, p.kind, p.status, p.encrypted_name,
                   p.created_at, p.updated_at, p.archived_at,
                   s.id AS snapshot_id, s.as_of_date, s.encrypted_payload, s.created_at AS snapshot_created_at
            FROM financial_position p
            LEFT JOIN LATERAL (
                SELECT id, as_of_date, encrypted_payload, created_at
                FROM financial_position_snapshot
                WHERE position_id = p.id
                ORDER BY as_of_date DESC, created_at DESC
                LIMIT 1
            ) s ON TRUE
            """;

    public void insert(
            Connection connection,
            UUID id,
            UUID householdId,
            PositionKind kind,
            byte[] encryptedName) throws SQLException {
        String sql = """
                INSERT INTO financial_position (id, household_id, kind, status, encrypted_name)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, householdId);
            statement.setString(3, kind.dbValue());
            statement.setString(4, PositionStatus.ACTIVE.dbValue());
            statement.setBytes(5, encryptedName);
            statement.executeUpdate();
        }
    }

    public void insertSnapshot(
            Connection connection,
            UUID snapshotId,
            UUID positionId,
            LocalDate asOfDate,
            byte[] encryptedPayload) throws SQLException {
        String sql = """
                INSERT INTO financial_position_snapshot (id, position_id, as_of_date, encrypted_payload)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, snapshotId);
            statement.setObject(2, positionId);
            statement.setDate(3, Date.valueOf(asOfDate));
            statement.setBytes(4, encryptedPayload);
            statement.executeUpdate();
        }
    }

    public Optional<FinancialPosition> findById(Connection connection, UUID householdId, UUID positionId)
            throws SQLException {
        String sql = POSITION_SELECT + " WHERE p.id = ? AND p.household_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, positionId);
            statement.setObject(2, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public List<FinancialPosition> listByHousehold(
            Connection connection,
            UUID householdId,
            boolean includeArchived) throws SQLException {
        String sql = includeArchived
                ? POSITION_SELECT + """
                WHERE p.household_id = ?
                ORDER BY p.created_at ASC
                """
                : POSITION_SELECT + """
                WHERE p.household_id = ? AND p.status = 'ACTIVE'
                ORDER BY p.created_at ASC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, householdId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<FinancialPosition> positions = new ArrayList<>();
                while (resultSet.next()) {
                    positions.add(mapRow(resultSet));
                }
                return positions;
            }
        }
    }

    public int countActive(Connection connection, UUID householdId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM financial_position
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

    public void updateEncryptedName(
            Connection connection,
            UUID householdId,
            UUID positionId,
            byte[] encryptedName) throws SQLException {
        String sql = """
                UPDATE financial_position
                SET encrypted_name = ?, updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, encryptedName);
            statement.setObject(2, positionId);
            statement.setObject(3, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active position not found");
            }
        }
    }

    public void touch(Connection connection, UUID householdId, UUID positionId) throws SQLException {
        String sql = """
                UPDATE financial_position
                SET updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, positionId);
            statement.setObject(2, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active position not found");
            }
        }
    }

    public void archive(Connection connection, UUID householdId, UUID positionId) throws SQLException {
        String sql = """
                UPDATE financial_position
                SET status = 'ARCHIVED', archived_at = NOW(), updated_at = NOW()
                WHERE id = ? AND household_id = ? AND status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, positionId);
            statement.setObject(2, householdId);
            if (statement.executeUpdate() == 0) {
                throw new SQLException("Active position not found");
            }
        }
    }

    private static FinancialPosition mapRow(ResultSet resultSet) throws SQLException {
        Timestamp archivedAt = resultSet.getTimestamp("archived_at");
        FinancialPositionSnapshot snapshot = null;
        UUID snapshotId = resultSet.getObject("snapshot_id", UUID.class);
        if (snapshotId != null) {
            snapshot = new FinancialPositionSnapshot(
                    snapshotId,
                    resultSet.getObject("id", UUID.class),
                    resultSet.getDate("as_of_date").toLocalDate(),
                    resultSet.getBytes("encrypted_payload"),
                    resultSet.getTimestamp("snapshot_created_at").toInstant());
        }
        return new FinancialPosition(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("household_id", UUID.class),
                PositionKind.fromDb(resultSet.getString("kind")),
                PositionStatus.fromDb(resultSet.getString("status")),
                resultSet.getBytes("encrypted_name"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                archivedAt == null ? null : archivedAt.toInstant(),
                snapshot);
    }
}
