-- C1 "Mục tiêu tiết kiệm": progress = the linked wallet's current balance / target_amount.
-- milestone_reached: the highest of 50/80/100 (%) already announced, so each milestone notifies once.
CREATE TABLE SAVINGS_GOALS (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id          BIGINT UNSIGNED NOT NULL,
    name               VARCHAR(100)    NOT NULL,
    target_amount      DECIMAL(18, 2)  NOT NULL,
    deadline           DATE            NULL,
    wallet_id          BIGINT UNSIGNED NOT NULL,
    milestone_reached  TINYINT         NOT NULL DEFAULT 0,
    archived           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    created_by_name    VARCHAR(100)    NULL,
    created_at         DATETIME        NOT NULL,
    CONSTRAINT chk_savings_goals_target CHECK (target_amount > 0),
    CONSTRAINT fk_savings_goals_wallet FOREIGN KEY (wallet_id) REFERENCES WALLETS (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_savings_goals_family ON SAVINGS_GOALS (family_id, archived);
