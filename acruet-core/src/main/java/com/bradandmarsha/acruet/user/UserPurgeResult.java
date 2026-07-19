package com.bradandmarsha.acruet.user;

import com.bradandmarsha.acruet.household.HouseholdDisplay;
import com.bradandmarsha.acruet.household.HouseholdMemberRole;

import java.util.UUID;

/**
 * Result of purging a provisioned user (Phase 12e).
 */
public record UserPurgeResult(
        UUID householdId,
        HouseholdMemberRole role,
        int membersBeforePurge,
        boolean householdDeleted) {

    public String auditDetail(boolean exportComplete) {
        String trigger = exportComplete ? "Purged after export complete" : "Purged after export deadline";
        if (householdDeleted) {
            return trigger
                    + "; household "
                    + HouseholdDisplay.shortId(householdId)
                    + " and ledger deleted (last member)";
        }
        return trigger
                + "; member removed from household "
                + HouseholdDisplay.shortId(householdId)
                + " ("
                + membersBeforePurge
                + " members before purge; shared ledger retained)";
    }

    public boolean sharedHouseholdRetained() {
        return !householdDeleted && membersBeforePurge > 1;
    }
}
