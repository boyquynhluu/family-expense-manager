ALTER TABLE USERS
    ADD COLUMN verification_token             VARCHAR(255) NULL,
    ADD COLUMN verification_token_expires_at   DATETIME     NULL,
    ALTER active SET DEFAULT 0;

CREATE UNIQUE INDEX uk_users_verification_token ON USERS (verification_token);
