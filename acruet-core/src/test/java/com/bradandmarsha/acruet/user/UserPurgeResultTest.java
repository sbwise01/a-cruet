package com.bradandmarsha.acruet.user;

import com.bradandmarsha.acruet.household.HouseholdMemberRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPurgeResultTest {

    @Test
    void auditDetailRetainedWhenOtherMembersRemain() {
        UUID householdId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UserPurgeResult result = new UserPurgeResult(householdId, HouseholdMemberRole.MEMBER, 2, false);
        String detail = result.auditDetail(true);
        assertTrue(detail.contains("shared ledger retained"));
        assertTrue(detail.contains("11111111"));
        assertTrue(result.sharedHouseholdRetained());
    }

    @Test
    void auditDetailDeletesHouseholdForLastMember() {
        UUID householdId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UserPurgeResult result = new UserPurgeResult(householdId, HouseholdMemberRole.OWNER, 1, true);
        String detail = result.auditDetail(false);
        assertTrue(detail.contains("ledger deleted (last member)"));
        assertFalse(result.sharedHouseholdRetained());
    }
}
