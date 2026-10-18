-- Envelope bundles: isolated ledger groups inside a household.
-- Existing envelopes and transactions attach to one default bundle per household.
-- Bundle names stay ciphertext; Flyway cannot write "Default".

CREATE TABLE envelope_bundle (
    id                      UUID PRIMARY KEY,
    household_id            UUID NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    status                  TEXT NOT NULL DEFAULT 'ACTIVE',
    encrypted_name          BYTEA,
    is_default              BOOLEAN NOT NULL DEFAULT FALSE,
    ledger_account_count    INT NOT NULL DEFAULT 0,
    ledger_account_limit    INT NOT NULL DEFAULT 100,
    transaction_count       INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at             TIMESTAMPTZ
);

CREATE UNIQUE INDEX envelope_bundle_household_default_idx
    ON envelope_bundle (household_id)
    WHERE is_default;

CREATE INDEX envelope_bundle_household_status_idx
    ON envelope_bundle (household_id, status, created_at);

ALTER TABLE household
    ADD COLUMN bundle_count INT NOT NULL DEFAULT 0;

DO $$
DECLARE
    household_row RECORD;
    new_bundle_id UUID;
BEGIN
    FOR household_row IN
        SELECT id, ledger_account_count, transaction_count
        FROM household
    LOOP
        new_bundle_id := gen_random_uuid();
        INSERT INTO envelope_bundle (
            id,
            household_id,
            status,
            is_default,
            ledger_account_count,
            ledger_account_limit,
            transaction_count
        ) VALUES (
            new_bundle_id,
            household_row.id,
            'ACTIVE',
            TRUE,
            household_row.ledger_account_count,
            100,
            household_row.transaction_count
        );
        UPDATE household
        SET bundle_count = 1, updated_at = NOW()
        WHERE id = household_row.id;
    END LOOP;
END $$;

ALTER TABLE ledger_account
    ADD COLUMN bundle_id UUID REFERENCES envelope_bundle (id);

UPDATE ledger_account account
SET bundle_id = bundle.id
FROM envelope_bundle bundle
WHERE bundle.household_id = account.household_id
  AND bundle.is_default;

ALTER TABLE ledger_account
    ALTER COLUMN bundle_id SET NOT NULL;

CREATE INDEX ledger_account_bundle_status_idx
    ON ledger_account (bundle_id, status, created_at);

ALTER TABLE ledger_transaction
    ADD COLUMN bundle_id UUID REFERENCES envelope_bundle (id);

UPDATE ledger_transaction txn
SET bundle_id = bundle.id
FROM envelope_bundle bundle
WHERE bundle.household_id = txn.household_id
  AND bundle.is_default;

ALTER TABLE ledger_transaction
    ALTER COLUMN bundle_id SET NOT NULL;

CREATE INDEX ledger_transaction_bundle_date_idx
    ON ledger_transaction (bundle_id, transaction_date DESC, created_at DESC);

UPDATE application_meta SET value = '1.1.0' WHERE key = 'schema_version';
