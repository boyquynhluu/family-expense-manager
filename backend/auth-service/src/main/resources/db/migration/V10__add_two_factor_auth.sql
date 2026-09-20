ALTER TABLE USERS
    ADD COLUMN totp_secret  VARCHAR(64) NULL AFTER is_system_admin,
    ADD COLUMN totp_enabled TINYINT(1)  NOT NULL DEFAULT 0 AFTER totp_secret;

CREATE TABLE TWO_FACTOR_RECOVERY_CODES (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    code_hash  VARCHAR(255)    NOT NULL,
    used_at    DATETIME        NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_two_factor_recovery_codes_user FOREIGN KEY (user_id) REFERENCES USERS (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_two_factor_recovery_codes_user_id ON TWO_FACTOR_RECOVERY_CODES (user_id);
