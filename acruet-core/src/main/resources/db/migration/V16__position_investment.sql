-- Opt-in investment flag on snapshot assets (PRODUCT.md §14). Ciphertext flowCents stays in the snapshot payload.

ALTER TABLE financial_position
    ADD COLUMN investment BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE financial_position
    ADD CONSTRAINT financial_position_investment_asset_chk
    CHECK (investment = false OR kind = 'ASSET');

UPDATE application_meta SET value = '1.6.0' WHERE key = 'schema_version';
