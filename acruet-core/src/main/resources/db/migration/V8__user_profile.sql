-- User-editable profile fields and withdraw preference.

ALTER TABLE acruet_user
    ADD COLUMN phone TEXT NOT NULL DEFAULT '',
    ADD COLUMN mailing_address TEXT NOT NULL DEFAULT '',
    ADD COLUMN allow_negative_withdraw BOOLEAN NOT NULL DEFAULT FALSE;

-- Copy signup metadata for users provisioned through the approval flow.
UPDATE acruet_user u
SET phone = sa.phone,
    mailing_address = sa.mailing_address
FROM signup_application sa
WHERE u.signup_application_id = sa.id;

UPDATE application_meta SET value = '0.8.0' WHERE key = 'schema_version';
