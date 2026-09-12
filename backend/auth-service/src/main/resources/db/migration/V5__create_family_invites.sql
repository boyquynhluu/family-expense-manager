CREATE TABLE FAMILY_INVITES (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id          BIGINT UNSIGNED NOT NULL,
    email              VARCHAR(255)    NOT NULL,
    token              VARCHAR(255)    NOT NULL,
    invited_by_user_id BIGINT UNSIGNED NOT NULL,
    expires_at         DATETIME        NOT NULL,
    accepted_at        DATETIME        NULL,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_family_invites_token UNIQUE (token),
    CONSTRAINT fk_family_invites_family FOREIGN KEY (family_id) REFERENCES FAMILIES (id),
    CONSTRAINT fk_family_invites_invited_by FOREIGN KEY (invited_by_user_id) REFERENCES USERS (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_family_invites_family_id ON FAMILY_INVITES (family_id);
