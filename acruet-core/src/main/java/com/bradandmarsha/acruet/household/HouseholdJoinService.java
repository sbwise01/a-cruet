package com.bradandmarsha.acruet.household;

import com.bradandmarsha.acruet.db.Database;
import com.bradandmarsha.acruet.signup.SignupRepository;
import com.bradandmarsha.acruet.signup.SignupTokens;
import com.bradandmarsha.acruet.user.AcruetUser;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Household member join key setup — unwrap invite DEK, store member wraps (Phase 12d).
 */
public final class HouseholdJoinService {

    private static final Logger LOGGER = Logger.getLogger(HouseholdJoinService.class.getName());

    private final HouseholdRepository householdRepository;
    private final HouseholdInviteRepository inviteRepository;
    private final SignupRepository signupRepository;

    public HouseholdJoinService(
            HouseholdRepository householdRepository,
            HouseholdInviteRepository inviteRepository,
            SignupRepository signupRepository) {
        this.householdRepository = householdRepository;
        this.inviteRepository = inviteRepository;
        this.signupRepository = signupRepository;
    }

    public static HouseholdJoinService fromEnvironment() {
        return new HouseholdJoinService(
                new HouseholdRepository(),
                new HouseholdInviteRepository(),
                new SignupRepository());
    }

    public static String initialKeySetupPath(AcruetUser user) {
        HouseholdJoinService service = fromEnvironment();
        return service.requiresHouseholdJoin(user) ? "/keys/join-household" : "/keys/setup";
    }

    public boolean requiresHouseholdJoin(AcruetUser user) {
        if (user.keySetupComplete()) {
            return false;
        }
        try {
            return Database.inTransactionReturning(connection -> {
                Optional<HouseholdMemberRole> role = householdRepository.findMemberRole(connection, user.id());
                if (role.isEmpty() || role.get() != HouseholdMemberRole.MEMBER) {
                    return false;
                }
                return signupRepository.hasHouseholdInviteApplication(connection, user.id());
            });
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Failed to evaluate household join requirement", exception);
            return false;
        }
    }

    public Optional<InviteWrapResponse> loadInviteWrap(AcruetUser user, String inviteToken, Instant now) {
        if (inviteToken == null || inviteToken.isBlank()) {
            return Optional.empty();
        }
        if (!requiresHouseholdJoin(user)) {
            return Optional.empty();
        }
        try {
            return Database.inTransactionReturning(connection -> inviteRepository
                    .findAcceptedInviteForMemberJoin(connection, user.id(), SignupTokens.hash(inviteToken.trim()))
                    .map(HouseholdJoinService::toWrapResponse));
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Failed to load household invite wrap", exception);
            return Optional.empty();
        }
    }

    private static InviteWrapResponse toWrapResponse(HouseholdInvite invite) {
        return new InviteWrapResponse(
                Base64.getEncoder().encodeToString(invite.encryptedInvitePayload()),
                invite.wrapAlgorithm(),
                invite.kdfAlgorithm(),
                invite.kdfHash(),
                Base64.getEncoder().encodeToString(invite.kdfSalt()),
                invite.kdfIterations());
    }

    public record InviteWrapResponse(
            String wrappedDek,
            String wrapAlgorithm,
            String kdfAlgorithm,
            String kdfHash,
            String kdfSalt,
            int kdfIterations) {
    }
}
