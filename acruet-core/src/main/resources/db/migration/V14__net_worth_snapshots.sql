-- Household net-worth observations (docs/NET_WORTH.md, PRODUCT.md §11).

CREATE TABLE net_worth_snapshot (
    id                  UUID PRIMARY KEY,
    household_id        UUID NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    as_of_date          DATE NOT NULL,
    encrypted_payload   BYTEA NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX net_worth_snapshot_latest_idx
    ON net_worth_snapshot (household_id, as_of_date DESC, created_at DESC);

ALTER TABLE household
    ADD COLUMN net_worth_snapshot_count INT NOT NULL DEFAULT 0;

UPDATE application_meta SET value = '1.4.0' WHERE key = 'schema_version';
