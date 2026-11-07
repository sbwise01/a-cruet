package com.bradandmarsha.acruet.position;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionInvestmentTest {

    @Test
    void defaultFlagIsOffForNewAssets() {
        FinancialPosition position = new FinancialPosition(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                PositionKind.ASSET,
                PositionStatus.ACTIVE,
                null,
                false,
                new byte[] {1},
                java.time.Instant.parse("2026-11-07T12:00:00Z"),
                java.time.Instant.parse("2026-11-07T12:00:00Z"),
                null,
                null);
        assertFalse(position.investment());
    }

    @Test
    void liabilitiesCannotBeInvestments() {
        PositionException exception = assertThrows(
                PositionException.class,
                () -> PositionService.requireInvestmentAllowed(PositionKind.LIABILITY, true));
        assertTrue(exception.getMessage().toLowerCase().contains("liabilit"));
    }

    @Test
    void assetsMayBeFlaggedAndUnflagged() {
        assertDoesNotThrow(() -> PositionService.requireInvestmentAllowed(PositionKind.ASSET, true));
        assertDoesNotThrow(() -> PositionService.requireInvestmentAllowed(PositionKind.ASSET, false));
        assertDoesNotThrow(() -> PositionService.requireInvestmentAllowed(PositionKind.LIABILITY, false));
    }
}
