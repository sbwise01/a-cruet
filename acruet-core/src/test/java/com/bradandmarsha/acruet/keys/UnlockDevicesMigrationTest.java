package com.bradandmarsha.acruet.keys;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlockDevicesMigrationTest {

    @Test
    void schemaCascadesDeleteAndUniquesCredentialId() throws IOException {
        String sql = new String(
                Objects.requireNonNull(
                                UnlockDevicesMigrationTest.class.getResourceAsStream(
                                        "/db/migration/V13__unlock_devices.sql"))
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("REFERENCES acruet_user (id) ON DELETE CASCADE"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX user_unlock_device_credential_id_idx"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX user_unlock_device_user_credential_idx"));
        assertTrue(sql.contains("1.3.0"));
    }
}
