package com.bradandmarsha.acruet.position;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionKindTest {

    @Test
    void dbRoundTrip() {
        assertEquals("ASSET", PositionKind.ASSET.dbValue());
        assertEquals(PositionKind.LIABILITY, PositionKind.fromDb("LIABILITY"));
        assertEquals("ARCHIVED", PositionStatus.ARCHIVED.dbValue());
        assertEquals(PositionStatus.ACTIVE, PositionStatus.fromDb("ACTIVE"));
    }

    @Test
    void maxActivePositionsIsFifty() {
        assertEquals(50, PositionService.MAX_ACTIVE_POSITIONS);
    }
}
