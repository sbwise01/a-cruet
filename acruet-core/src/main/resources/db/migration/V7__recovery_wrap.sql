-- Phase 7.1: independent recovery wrap for forgot-passphrase (file-only recovery secret).

ALTER TABLE user_encryption_key
    ADD COLUMN recovery_wrapped_dek BYTEA,
    ADD COLUMN recovery_wrap_algorithm TEXT;

UPDATE application_meta SET value = '0.7.0' WHERE key = 'schema_version';
