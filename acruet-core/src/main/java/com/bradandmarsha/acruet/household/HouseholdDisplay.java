package com.bradandmarsha.acruet.household;

import java.util.UUID;

/**
 * Admin-facing household labels (Phase 12f).
 */
public final class HouseholdDisplay {

    private HouseholdDisplay() {
    }

    public static String shortId(UUID householdId) {
        if (householdId == null) {
            return "—";
        }
        return householdId.toString().replace("-", "").substring(0, 8);
    }

    public static String membershipSummary(UUID householdId, HouseholdMemberRole role, int memberCount) {
        if (householdId == null || role == null || memberCount <= 0) {
            return "—";
        }
        return shortId(householdId) + " · " + role.dbValue() + " · " + memberCount + " member"
                + (memberCount == 1 ? "" : "s");
    }
}
