package com.bradandmarsha.acruet.networth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link NetWorthSnapshot} rows.
 */
public interface NetWorthStore {

    List<NetWorthSnapshot> listByHousehold(UUID householdId);

    Optional<NetWorthSnapshot> findLatest(UUID householdId);

    int countByHousehold(UUID householdId);

    void insert(NetWorthSnapshot snapshot);

    void deleteAllForHousehold(UUID householdId);
}
