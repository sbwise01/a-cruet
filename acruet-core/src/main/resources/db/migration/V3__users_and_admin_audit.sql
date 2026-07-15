-- Phase 6: provisioned users, admin action audit.

CREATE TABLE acruet_user (
    id                      UUID PRIMARY KEY,
    keycloak_user_id        TEXT NOT NULL UNIQUE,
    email                   TEXT NOT NULL,
    display_name            TEXT NOT NULL,
    signup_application_id   UUID REFERENCES signup_application (id),
    ledger_account_count    INT NOT NULL DEFAULT 0,
    transaction_count       INT NOT NULL DEFAULT 0,
    key_setup_complete      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at           TIMESTAMPTZ
);

CREATE UNIQUE INDEX acruet_user_email_idx ON acruet_user (LOWER(email));

CREATE TABLE admin_action_audit (
    id                      BIGSERIAL PRIMARY KEY,
    admin_keycloak_user_id  TEXT NOT NULL,
    admin_email             TEXT,
    action                  TEXT NOT NULL,
    target_type             TEXT NOT NULL,
    target_id               UUID NOT NULL,
    detail                  TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX admin_action_audit_target_idx ON admin_action_audit (target_type, target_id, created_at DESC);

UPDATE application_meta SET value = '0.3.0' WHERE key = 'schema_version';
