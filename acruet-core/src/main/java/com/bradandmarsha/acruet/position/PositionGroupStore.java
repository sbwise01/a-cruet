package com.bradandmarsha.acruet.position;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for household position groups (PRODUCT.md §12).
 */
public interface PositionGroupStore {

    List<PositionGroup> listByHousehold(UUID householdId, boolean includeArchived);

    Optional<PositionGroup> findById(UUID householdId, UUID groupId);

    int countActive(UUID householdId);

    int countActivePositions(UUID householdId, UUID groupId);

    void insert(PositionGroup group);

    void updateEncryptedName(UUID householdId, UUID groupId, byte[] encryptedName);

    void archive(UUID householdId, UUID groupId);

    void deleteAllForHousehold(UUID householdId);
}
