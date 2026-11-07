package com.bradandmarsha.acruet.position;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionInvestmentMigrationTest {

    @Test
    void schemaAddsInvestmentFlagAndBumpsVersion() throws IOException {
        String sql = new String(
                Objects.requireNonNull(
                                PositionInvestmentMigrationTest.class.getResourceAsStream(
                                        "/db/migration/V16__position_investment.sql"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD COLUMN investment BOOLEAN NOT NULL DEFAULT false"));
        assertTrue(sql.contains("financial_position_investment_asset_chk"));
        assertTrue(sql.contains("investment = false OR kind = 'ASSET'"));
        assertTrue(sql.contains("1.6.0"));
    }
}
