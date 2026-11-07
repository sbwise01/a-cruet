package com.bradandmarsha.acruet.position;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionGroupMigrationTest {

    @Test
    void schemaAddsNullableGroupIdAndBumpsVersion() throws IOException {
        String sql = new String(
                Objects.requireNonNull(
                                PositionGroupMigrationTest.class.getResourceAsStream(
                                        "/db/migration/V15__position_groups.sql"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TABLE financial_position_group"));
        assertTrue(sql.contains("REFERENCES household (id) ON DELETE CASCADE"));
        assertTrue(sql.contains("ADD COLUMN group_id"));
        assertTrue(sql.contains("ON DELETE SET NULL"));
        assertTrue(sql.contains("financial_position_household_group_idx"));
        assertTrue(sql.contains("position_group_count"));
        assertTrue(sql.contains("1.5.0"));
    }
}
