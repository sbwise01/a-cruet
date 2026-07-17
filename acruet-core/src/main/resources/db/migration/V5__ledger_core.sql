-- Phase 8: ledger accounts and append-only transactions (ciphertext only).

ALTER TABLE acruet_user
    ADD COLUMN ledger_account_limit INT NOT NULL DEFAULT 100,
    ADD COLUMN last_transaction_at TIMESTAMPTZ;

CREATE TABLE ledger_account (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES acruet_user (id) ON DELETE CASCADE,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    encrypted_name  BYTEA NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at     TIMESTAMPTZ
);

CREATE INDEX ledger_account_user_status_idx ON ledger_account (user_id, status, created_at);

CREATE TABLE ledger_transaction (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES acruet_user (id) ON DELETE CASCADE,
    transaction_type  TEXT NOT NULL,
    transaction_date  DATE NOT NULL,
    encrypted_payload BYTEA NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ledger_transaction_user_date_idx
    ON ledger_transaction (user_id, transaction_date DESC, created_at DESC);

CREATE TABLE ledger_transaction_account (
    transaction_id UUID NOT NULL REFERENCES ledger_transaction (id) ON DELETE CASCADE,
    account_id     UUID NOT NULL REFERENCES ledger_account (id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, account_id)
);

CREATE INDEX ledger_transaction_account_account_idx
    ON ledger_transaction_account (account_id, transaction_id);

CREATE TABLE ledger_write_attempt (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES acruet_user (id) ON DELETE CASCADE,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ledger_write_attempt_user_idx ON ledger_write_attempt (user_id, attempted_at DESC);

UPDATE application_meta SET value = '0.5.0' WHERE key = 'schema_version';
