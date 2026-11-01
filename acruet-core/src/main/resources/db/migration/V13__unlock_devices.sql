-- Per-user WebAuthn device wraps of the household DEK (docs/DEVICE_UNLOCK.md).

CREATE TABLE user_unlock_device (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES acruet_user (id) ON DELETE CASCADE,
    credential_id   BYTEA NOT NULL,
    wrapped_dek     BYTEA NOT NULL,
    wrap_algorithm  TEXT NOT NULL DEFAULT 'AES-KW',
    display_name    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX user_unlock_device_credential_id_idx
    ON user_unlock_device (credential_id);

CREATE UNIQUE INDEX user_unlock_device_user_credential_idx
    ON user_unlock_device (user_id, credential_id);

CREATE INDEX user_unlock_device_user_id_idx
    ON user_unlock_device (user_id, created_at);

UPDATE application_meta SET value = '1.3.0' WHERE key = 'schema_version';
