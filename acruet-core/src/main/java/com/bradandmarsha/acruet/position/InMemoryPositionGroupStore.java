package com.bradandmarsha.acruet.position;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process store for unit tests.
 */
public final class InMemoryPositionGroupStore implements PositionGroupStore {

    private static final Comparator<PositionGroup> CREATED = Comparator.comparing(PositionGroup::createdAt);

    private final List<PositionGroup> rows = new CopyOnWriteArrayList<>();
    private final Map<UUID, Set<UUID>> activePositionIdsByGroup = new ConcurrentHashMap<>();

    @Override
    public List<PositionGroup> listByHousehold(UUID householdId, boolean includeArchived) {
        return rows.stream()
                .filter(group -> group.householdId().equals(householdId))
                .filter(group -> includeArchived || group.status() == PositionStatus.ACTIVE)
                .sorted(CREATED)
                .toList();
    }

    @Override
    public Optional<PositionGroup> findById(UUID householdId, UUID groupId) {
        return rows.stream()
                .filter(group -> group.householdId().equals(householdId) && group.id().equals(groupId))
                .findFirst();
    }

    @Override
    public int countActive(UUID householdId) {
        return (int) rows.stream()
                .filter(group -> group.householdId().equals(householdId) && group.status() == PositionStatus.ACTIVE)
                .count();
    }

    @Override
    public int countActivePositions(UUID householdId, UUID groupId) {
        findById(householdId, groupId).orElseThrow(() -> new PositionException("Group not found"));
        return activePositionIdsByGroup.getOrDefault(groupId, Set.of()).size();
    }

    @Override
    public void insert(PositionGroup group) {
        rows.add(group);
    }

    @Override
    public void updateEncryptedName(UUID householdId, UUID groupId, byte[] encryptedName) {
        PositionGroup current = requireActive(householdId, groupId);
        replace(current, new PositionGroup(
                current.id(),
                current.householdId(),
                current.status(),
                encryptedName,
                current.createdAt(),
                Instant.now(),
                current.archivedAt()));
    }

    @Override
    public void archive(UUID householdId, UUID groupId) {
        PositionGroup current = requireActive(householdId, groupId);
        Instant now = Instant.now();
        replace(current, new PositionGroup(
                current.id(),
                current.householdId(),
                PositionStatus.ARCHIVED,
                current.encryptedName(),
                current.createdAt(),
                now,
                now));
    }

    @Override
    public void deleteAllForHousehold(UUID householdId) {
        List<UUID> removed = rows.stream()
                .filter(group -> group.householdId().equals(householdId))
                .map(PositionGroup::id)
                .toList();
        rows.removeIf(group -> group.householdId().equals(householdId));
        removed.forEach(activePositionIdsByGroup::remove);
    }

    public void recordActivePosition(UUID groupId, UUID positionId) {
        activePositionIdsByGroup.computeIfAbsent(groupId, ignored -> ConcurrentHashMap.newKeySet()).add(positionId);
    }

    private PositionGroup requireActive(UUID householdId, UUID groupId) {
        PositionGroup group = findById(householdId, groupId)
                .orElseThrow(() -> new PositionException("Group not found"));
        if (group.status() != PositionStatus.ACTIVE) {
            throw new PositionException("Group is archived.");
        }
        return group;
    }

    private void replace(PositionGroup current, PositionGroup next) {
        int index = rows.indexOf(current);
        if (index < 0) {
            throw new PositionException("Group not found");
        }
        rows.set(index, next);
    }
}
