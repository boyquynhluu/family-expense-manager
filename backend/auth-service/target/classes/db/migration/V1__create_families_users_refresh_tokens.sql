CREATE TABLE FAMILIES (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE USERS (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id     BIGINT UNSIGNED NOT NULL,
    email         VARCHAR(255)    NOT NULL,
    password_hash VARCHAR(255)    NOT NULL,
    display_name  VARCHAR(255)    NOT NULL,
    role          VARCHAR(30)     NOT NULL,
    active        TINYINT(1)      NOT NULL DEFAULT 1,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_family FOREIGN KEY (family_id) REFERENCES FAMILIES (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_users_family_id ON USERS (family_id);

CREATE TABLE REFRESH_TOKENS (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(255)    NOT NULL,
    expires_at DATETIME        NOT NULL,
    revoked    TINYINT(1)      NOT NULL DEFAULT 0,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES USERS (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_refresh_tokens_user_id ON REFRESH_TOKENS (user_id);
