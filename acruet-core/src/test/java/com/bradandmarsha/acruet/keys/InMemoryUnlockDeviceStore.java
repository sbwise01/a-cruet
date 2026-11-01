package com.bradandmarsha.acruet.keys;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class InMemoryUnlockDeviceStore implements UnlockDeviceStore {

    private final List<UserUnlockDevice> devices = new CopyOnWriteArrayList<>();

    @Override
    public List<UserUnlockDevice> listByUser(UUID userId) {
        List<UserUnlockDevice> matches = new ArrayList<>();
        for (UserUnlockDevice device : devices) {
            if (device.userId().equals(userId)) {
                matches.add(device);
            }
        }
        return matches;
    }

    @Override
    public int countByUser(UUID userId) {
        return listByUser(userId).size();
    }

    @Override
    public Optional<UserUnlockDevice> findById(UUID userId, UUID id) {
        return devices.stream()
                .filter(device -> device.userId().equals(userId) && device.id().equals(id))
                .findFirst();
    }

    @Override
    public Optional<UserUnlockDevice> findByCredentialId(byte[] credentialId) {
        return devices.stream()
                .filter(device -> Arrays.equals(device.credentialId(), credentialId))
                .findFirst();
    }

    @Override
    public void insert(UserUnlockDevice device) {
        if (findByCredentialId(device.credentialId()).isPresent()) {
            throw new DeviceUnlockException("That authenticator is already registered.");
        }
        devices.add(device);
    }

    @Override
    public boolean updateDisplayName(UUID userId, UUID id, String displayName) {
        Optional<UserUnlockDevice> found = findById(userId, id);
        if (found.isEmpty()) {
            return false;
        }
        UserUnlockDevice current = found.get();
        devices.remove(current);
        devices.add(new UserUnlockDevice(
                current.id(),
                current.userId(),
                current.credentialId(),
                current.wrappedDek(),
                current.wrapAlgorithm(),
                displayName,
                current.createdAt(),
                current.lastUsedAt()));
        return true;
    }

    @Override
    public boolean delete(UUID userId, UUID id) {
        return devices.removeIf(device -> device.userId().equals(userId) && device.id().equals(id));
    }

    @Override
    public void touchLastUsed(UUID userId, UUID id, Instant at) {
        findById(userId, id).ifPresent(current -> {
            devices.remove(current);
            devices.add(new UserUnlockDevice(
                    current.id(),
                    current.userId(),
                    current.credentialId(),
                    current.wrappedDek(),
                    current.wrapAlgorithm(),
                    current.displayName(),
                    current.createdAt(),
                    at));
        });
    }

    @Override
    public void deleteAllForUser(UUID userId) {
        devices.removeIf(device -> device.userId().equals(userId));
    }
}
