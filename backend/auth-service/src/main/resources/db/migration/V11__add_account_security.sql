ALTER TABLE USERS
    ADD COLUMN locked                   TINYINT(1)   NOT NULL DEFAULT 0,
    ADD COLUMN locked_at                DATETIME     NULL,
    ADD COLUMN pending_email            VARCHAR(255) NULL,
    ADD COLUMN pending_email_token      VARCHAR(255) NULL,
    ADD COLUMN pending_email_expires_at DATETIME     NULL,
    ADD COLUMN totp_last_step           BIGINT       NULL,
    MODIFY COLUMN totp_secret           VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_users_pending_email_token ON USERS (pending_email_token);
