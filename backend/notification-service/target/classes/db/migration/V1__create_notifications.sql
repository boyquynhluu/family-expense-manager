CREATE TABLE NOTIFICATIONS (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id    BIGINT UNSIGNED NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    type         VARCHAR(30)     NOT NULL,
    title        VARCHAR(255)    NOT NULL,
    message      VARCHAR(1000)   NOT NULL,
    payload_json JSON            NULL,
    is_read      TINYINT(1)      NOT NULL DEFAULT 0
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_notifications_family_id ON NOTIFICATIONS (family_id);
CREATE INDEX idx_notifications_user_id ON NOTIFICATIONS (user_id);
