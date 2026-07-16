-- Phase 7: wrapped DEK storage (passphrase-derived KEK stays client-side only).

CREATE TABLE user_encryption_key (
    user_id         UUID PRIMARY KEY REFERENCES acruet_user (id) ON DELETE CASCADE,
    wrapped_dek     BYTEA NOT NULL,
    wrap_algorithm  TEXT NOT NULL DEFAULT 'AES-KW',
    kdf_algorithm   TEXT NOT NULL DEFAULT 'PBKDF2',
    kdf_hash        TEXT NOT NULL DEFAULT 'SHA-256',
    kdf_salt        BYTEA NOT NULL,
    kdf_iterations  INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX user_encryption_key_updated_at_idx ON user_encryption_key (updated_at);
