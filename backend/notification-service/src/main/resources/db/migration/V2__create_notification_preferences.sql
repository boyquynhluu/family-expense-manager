CREATE TABLE NOTIFICATION_PREFERENCES (
    user_id        BIGINT UNSIGNED NOT NULL,
    type           VARCHAR(30)     NOT NULL,
    in_app_enabled TINYINT(1)      NOT NULL DEFAULT 1,
    email_enabled  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
