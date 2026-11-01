package com.bradandmarsha.acruet.keys;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link UserUnlockDevice} rows.
 */
public interface UnlockDeviceStore {

    List<UserUnlockDevice> listByUser(UUID userId);

    int countByUser(UUID userId);

    Optional<UserUnlockDevice> findById(UUID userId, UUID id);

    Optional<UserUnlockDevice> findByCredentialId(byte[] credentialId);

    void insert(UserUnlockDevice device);

    boolean updateDisplayName(UUID userId, UUID id, String displayName);

    boolean delete(UUID userId, UUID id);

    void touchLastUsed(UUID userId, UUID id, Instant at);

    void deleteAllForUser(UUID userId);
}
