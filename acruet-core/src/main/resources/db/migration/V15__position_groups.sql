-- Household labels for snapshot items (PRODUCT.md §12). Names are ciphertext.

CREATE TABLE financial_position_group (
    id              UUID PRIMARY KEY,
    household_id    UUID NOT NULL REFERENCES household (id) ON DELETE CASCADE,
    encrypted_name  BYTEA NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at     TIMESTAMPTZ
);

CREATE INDEX financial_position_group_household_status_idx
    ON financial_position_group (household_id, status, created_at);

ALTER TABLE financial_position
    ADD COLUMN group_id UUID REFERENCES financial_position_group (id) ON DELETE SET NULL;

CREATE INDEX financial_position_household_group_idx
    ON financial_position (household_id, group_id);

ALTER TABLE household
    ADD COLUMN position_group_count INT NOT NULL DEFAULT 0;

UPDATE application_meta SET value = '1.5.0' WHERE key = 'schema_version';
