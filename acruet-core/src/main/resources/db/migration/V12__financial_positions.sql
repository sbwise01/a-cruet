-- Snapshot assets and liabilities (PRODUCT.md §9). Separate from the envelope ledger.

CREATE TABLE financial_position (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    kind            TEXT NOT NULL CHECK (kind IN ('ASSET', 'LIABILITY')),
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    encrypted_name  BYTEA NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at     TIMESTAMPTZ
);

CREATE INDEX financial_position_household_status_idx
    ON financial_position (household_id, status, created_at);

CREATE TABLE financial_position_snapshot (
    id                  UUID PRIMARY KEY,
    position_id         UUID NOT NULL REFERENCES financial_position (id) ON DELETE CASCADE,
    as_of_date          DATE NOT NULL,
    encrypted_payload   BYTEA NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX financial_position_snapshot_latest_idx
    ON financial_position_snapshot (position_id, as_of_date DESC, created_at DESC);

ALTER TABLE household
    ADD COLUMN position_count INT NOT NULL DEFAULT 0;

UPDATE application_meta SET value = '1.2.0' WHERE key = 'schema_version';
