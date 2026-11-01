package com.bradandmarsha.acruet.networth;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process store for unit tests.
 */
public final class InMemoryNetWorthStore implements NetWorthStore {

    private static final Comparator<NetWorthSnapshot> CHRONOLOGICAL = Comparator
            .comparing(NetWorthSnapshot::asOfDate)
            .thenComparing(NetWorthSnapshot::createdAt);

    private final List<NetWorthSnapshot> rows = new CopyOnWriteArrayList<>();

    @Override
    public List<NetWorthSnapshot> listByHousehold(UUID householdId) {
        return rows.stream()
                .filter(snapshot -> snapshot.householdId().equals(householdId))
                .sorted(CHRONOLOGICAL)
                .toList();
    }

    @Override
    public Optional<NetWorthSnapshot> findLatest(UUID householdId) {
        return rows.stream()
                .filter(snapshot -> snapshot.householdId().equals(householdId))
                .max(CHRONOLOGICAL);
    }

    @Override
    public int countByHousehold(UUID householdId) {
        return (int) rows.stream()
                .filter(snapshot -> snapshot.householdId().equals(householdId))
                .count();
    }

    @Override
    public void insert(NetWorthSnapshot snapshot) {
        rows.add(snapshot);
    }

    @Override
    public void deleteAllForHousehold(UUID householdId) {
        rows.removeIf(snapshot -> snapshot.householdId().equals(householdId));
    }
}
