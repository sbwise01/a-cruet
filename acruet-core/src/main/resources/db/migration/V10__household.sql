-- Phase 12a: shared household ledger — schema, backfill, ledger scope by household_id.

CREATE TABLE household (
    id                      UUID PRIMARY KEY,
    ledger_account_count    INT NOT NULL DEFAULT 0,
    transaction_count       INT NOT NULL DEFAULT 0,
    ledger_account_limit    INT NOT NULL DEFAULT 100,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE household_member (
    household_id    UUID NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL PRIMARY KEY REFERENCES acruet_user (id) ON DELETE CASCADE,
    role            TEXT NOT NULL CHECK (role IN ('owner', 'member')),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX household_member_household_idx ON household_member (household_id);

CREATE TABLE household_invite (
    id                          UUID PRIMARY KEY,
    household_id                UUID NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    email                       TEXT NOT NULL,
    token_hash                  TEXT NOT NULL,
    status                      TEXT NOT NULL CHECK (status IN ('pending', 'accepted', 'revoked', 'expired')),
    invited_by_user_id          UUID NOT NULL REFERENCES acruet_user (id),
    encrypted_invite_payload    BYTEA NOT NULL,
    wrap_algorithm              TEXT NOT NULL DEFAULT 'AES-KW',
    kdf_algorithm               TEXT NOT NULL DEFAULT 'PBKDF2',
    kdf_hash                    TEXT NOT NULL DEFAULT 'SHA-256',
    kdf_salt                    BYTEA NOT NULL,
    kdf_iterations              INT NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX household_invite_token_hash_idx ON household_invite (token_hash);
CREATE INDEX household_invite_household_status_idx ON household_invite (household_id, status);
CREATE INDEX household_invite_email_idx ON household_invite (LOWER(email), created_at DESC);

ALTER TABLE signup_application
    ADD COLUMN household_invite_id UUID REFERENCES household_invite (id);

-- Backfill one household per existing user (owner role).
ALTER TABLE acruet_user
    ADD COLUMN household_id UUID REFERENCES household (id);

DO $$
DECLARE
    user_row RECORD;
    new_household_id UUID;
BEGIN
    FOR user_row IN
        SELECT id, ledger_account_count, transaction_count, ledger_account_limit, created_at
        FROM acruet_user
    LOOP
        new_household_id := gen_random_uuid();
        INSERT INTO household (
            id, ledger_account_count, transaction_count, ledger_account_limit, created_at, updated_at
        ) VALUES (
            new_household_id,
            user_row.ledger_account_count,
            user_row.transaction_count,
            user_row.ledger_account_limit,
            user_row.created_at,
            user_row.created_at
        );
        UPDATE acruet_user SET household_id = new_household_id WHERE id = user_row.id;
        INSERT INTO household_member (household_id, user_id, role, joined_at)
        VALUES (new_household_id, user_row.id, 'owner', user_row.created_at);
    END LOOP;
END $$;

ALTER TABLE acruet_user
    ALTER COLUMN household_id SET NOT NULL;

ALTER TABLE ledger_account
    ADD COLUMN household_id UUID REFERENCES household (id);

UPDATE ledger_account la
SET household_id = u.household_id
FROM acruet_user u
WHERE la.user_id = u.id;

ALTER TABLE ledger_account
    ALTER COLUMN household_id SET NOT NULL;

DROP INDEX ledger_account_user_status_idx;

ALTER TABLE ledger_account
    DROP COLUMN user_id;

CREATE INDEX ledger_account_household_status_idx
    ON ledger_account (household_id, status, created_at);

ALTER TABLE ledger_transaction
    ADD COLUMN household_id UUID REFERENCES household (id);

UPDATE ledger_transaction lt
SET household_id = u.household_id
FROM acruet_user u
WHERE lt.user_id = u.id;

ALTER TABLE ledger_transaction
    ALTER COLUMN household_id SET NOT NULL;

DROP INDEX ledger_transaction_user_date_idx;

ALTER TABLE ledger_transaction
    DROP COLUMN user_id;

CREATE INDEX ledger_transaction_household_date_idx
    ON ledger_transaction (household_id, transaction_date DESC, created_at DESC);

ALTER TABLE acruet_user
    DROP COLUMN ledger_account_count,
    DROP COLUMN transaction_count,
    DROP COLUMN ledger_account_limit;

UPDATE application_meta SET value = '1.0.0' WHERE key = 'schema_version';
