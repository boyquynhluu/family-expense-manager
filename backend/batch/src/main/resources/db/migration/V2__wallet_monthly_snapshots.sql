-- walletMonthlySnapshotJob: each wallet's balance at the end of a month, with every component of the formula
-- (WalletService.currentBalanceOf) so a drift can be traced to its source. One row per (month, wallet); the job
-- upserts on that key, so a re-written chunk never duplicates a row.
CREATE TABLE WALLET_MONTHLY_SNAPSHOTS (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    -- 'yyyy-MM'. Not named year_month: YEAR_MONTH is a reserved word in MySQL (an INTERVAL unit).
    snapshot_month  CHAR(7)         NOT NULL,
    wallet_id       BIGINT UNSIGNED NOT NULL,
    family_id       BIGINT UNSIGNED NOT NULL,
    -- as it was when the snapshot was taken (a wallet can be renamed later)
    wallet_name     VARCHAR(255)    NOT NULL,
    currency        CHAR(3)         NOT NULL,
    initial_balance DECIMAL(18, 2)  NOT NULL,
    income          DECIMAL(18, 2)  NOT NULL,
    expense         DECIMAL(18, 2)  NOT NULL,
    transfer_in     DECIMAL(18, 2)  NOT NULL,
    transfer_out    DECIMAL(18, 2)  NOT NULL,
    adjustments     DECIMAL(18, 2)  NOT NULL,
    loans           DECIMAL(18, 2)  NOT NULL,
    closing_balance DECIMAL(18, 2)  NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_monthly_snapshots_month_wallet (snapshot_month, wallet_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_wallet_monthly_snapshots_family_month ON WALLET_MONTHLY_SNAPSHOTS (family_id, snapshot_month);
