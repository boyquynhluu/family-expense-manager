-- B1 "Điều chỉnh số dư": when a wallet's balance in the app drifts from the real one (a forgotten cash
-- expense, bank fees...), the member records the REAL balance and the difference is stored here. Like
-- WALLET_TRANSFERS it only moves the wallet balance — never income/expense, budgets or category reports, so
-- reconciling no longer needs a fake income/expense that skews them.
-- amount is signed (balance_after - balance_before); balance_before/after are kept as they were at that moment.
-- ON DELETE CASCADE: an adjustment means nothing without its wallet, so purging a wallet from the trash takes
-- its adjustments with it (a wallet that still has some cannot be moved to the trash in the first place).
CREATE TABLE WALLET_ADJUSTMENTS (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id          BIGINT UNSIGNED NOT NULL,
    wallet_id          BIGINT UNSIGNED NOT NULL,
    amount             DECIMAL(18, 2)  NOT NULL,
    balance_before     DECIMAL(18, 2)  NOT NULL,
    balance_after      DECIMAL(18, 2)  NOT NULL,
    note               VARCHAR(255)    NULL,
    occurred_at        DATETIME        NOT NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    created_by_name    VARCHAR(100)    NULL,
    created_at         DATETIME        NOT NULL,
    CONSTRAINT chk_wallet_adjustments_amount CHECK (amount <> 0),
    CONSTRAINT fk_wallet_adjustments_wallet FOREIGN KEY (wallet_id) REFERENCES WALLETS (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_wallet_adjustments_family_occurred_at ON WALLET_ADJUSTMENTS (family_id, occurred_at);
CREATE INDEX idx_wallet_adjustments_wallet_id ON WALLET_ADJUSTMENTS (wallet_id);

-- B2 "Chốt sổ theo tháng": one row per closed month. While it exists, nothing dated in that month may be
-- created, edited, deleted or restored (transactions, transfers, adjustments, import), so balances and
-- budgets of a settled month can no longer change behind anyone's back. Only the family OWNER locks/unlocks.
CREATE TABLE PERIOD_LOCKS (
    family_id         BIGINT UNSIGNED NOT NULL,
    period_month      CHAR(7)         NOT NULL,
    locked_by_user_id BIGINT UNSIGNED NOT NULL,
    locked_by_name    VARCHAR(100)    NULL,
    locked_at         DATETIME        NOT NULL,
    PRIMARY KEY (family_id, period_month)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Every lock and unlock, kept even after a month is reopened (who reopened a settled month, and when).
CREATE TABLE PERIOD_LOCK_LOGS (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id     BIGINT UNSIGNED NOT NULL,
    period_month  CHAR(7)         NOT NULL,
    action        VARCHAR(10)     NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    actor_name    VARCHAR(100)    NULL,
    created_at    DATETIME        NOT NULL,
    CONSTRAINT chk_period_lock_logs_action CHECK (action IN ('LOCKED', 'UNLOCKED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_period_lock_logs_family_created_at ON PERIOD_LOCK_LOGS (family_id, created_at);
