ALTER TABLE USERS
    ADD COLUMN reset_password_token             VARCHAR(255) NULL,
    ADD COLUMN reset_password_token_expires_at   DATETIME     NULL;

CREATE UNIQUE INDEX uk_users_reset_password_token ON USERS (reset_password_token);
