package com.bradandmarsha.acruet.ledger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvelopeBundleStatusTest {

    @Test
    void dbRoundTrip() {
        assertEquals("ACTIVE", EnvelopeBundleStatus.ACTIVE.dbValue());
        assertEquals(EnvelopeBundleStatus.ARCHIVED, EnvelopeBundleStatus.fromDb("ARCHIVED"));
    }

    @Test
    void maxActiveBundlesIsTen() {
        assertEquals(10, LedgerService.MAX_ACTIVE_BUNDLES);
    }
}
