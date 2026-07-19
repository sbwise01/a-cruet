-- Phase 11: suspension, offboarding, login anomaly alerting.

ALTER TABLE acruet_user
    ADD COLUMN suspended_until TIMESTAMPTZ,
    ADD COLUMN suspended_at TIMESTAMPTZ;

CREATE TABLE user_offboard (
    user_id                         UUID PRIMARY KEY REFERENCES acruet_user (id) ON DELETE CASCADE,
    initiated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    export_deadline                 TIMESTAMPTZ NOT NULL,
    export_completed_at             TIMESTAMPTZ,
    purged_at                       TIMESTAMPTZ,
    initiated_by_keycloak_user_id   TEXT NOT NULL,
    initiated_by_email              TEXT
);

CREATE INDEX user_offboard_purge_idx ON user_offboard (export_deadline)
    WHERE purged_at IS NULL;

ALTER TABLE login_anomaly
    ADD COLUMN alerted_at TIMESTAMPTZ;

UPDATE application_meta SET value = '0.9.0' WHERE key = 'schema_version';
