package com.bradandmarsha.acruet.networth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NetWorthMigrationTest {

    @Test
    void schemaCascadesHouseholdDeleteAndBumpsVersion() throws IOException {
        String sql = new String(
                Objects.requireNonNull(
                                NetWorthMigrationTest.class.getResourceAsStream(
                                        "/db/migration/V14__net_worth_snapshots.sql"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("REFERENCES household (id) ON DELETE CASCADE"));
        assertTrue(sql.contains("net_worth_snapshot_latest_idx"));
        assertTrue(sql.contains("net_worth_snapshot_count"));
        assertTrue(sql.contains("1.4.0"));
    }
}
