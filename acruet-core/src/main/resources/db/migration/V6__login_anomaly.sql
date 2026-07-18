-- Phase 9: record Keycloak sessions with no matching acruet_user (admin alert in Phase 11).

CREATE TABLE login_anomaly (
    id                 BIGSERIAL PRIMARY KEY,
    keycloak_user_id   TEXT NOT NULL,
    email              TEXT,
    detail             TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX login_anomaly_keycloak_idx ON login_anomaly (keycloak_user_id, created_at DESC);

UPDATE application_meta SET value = '0.6.0' WHERE key = 'schema_version';
