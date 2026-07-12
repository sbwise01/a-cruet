-- a-cruet schema baseline (Phase 1).
-- Application, ledger, and admin tables are added in later rollout phases.

CREATE TABLE IF NOT EXISTS application_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO application_meta (key, value)
VALUES ('schema_version', '0.1.0')
ON CONFLICT (key) DO NOTHING;
