-- Phase 5: public signup applications (plaintext metadata) and rate-limit audit.

CREATE TABLE signup_application (
    id                          UUID PRIMARY KEY,
    email                       TEXT NOT NULL,
    full_name                   TEXT NOT NULL,
    reason                      TEXT NOT NULL,
    phone                       TEXT NOT NULL,
    mailing_address             TEXT NOT NULL,
    status                      TEXT NOT NULL,
    rejection_count             INT NOT NULL DEFAULT 0,
    verification_token_hash     TEXT,
    verification_token_expires_at TIMESTAMPTZ,
    verified_at                 TIMESTAMPTZ,
    last_rejected_at            TIMESTAMPTZ,
    applicant_ip                TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX signup_application_email_idx ON signup_application (LOWER(email), created_at DESC);
CREATE INDEX signup_application_token_hash_idx ON signup_application (verification_token_hash)
    WHERE verification_token_hash IS NOT NULL;

CREATE TABLE signup_attempt (
    id            BIGSERIAL PRIMARY KEY,
    email         TEXT,
    ip_address    TEXT NOT NULL,
    attempted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX signup_attempt_ip_idx ON signup_attempt (ip_address, attempted_at);
CREATE INDEX signup_attempt_email_idx ON signup_attempt (LOWER(email), attempted_at);

UPDATE application_meta SET value = '0.2.0' WHERE key = 'schema_version';
