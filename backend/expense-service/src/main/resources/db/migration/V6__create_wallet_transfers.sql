CREATE TABLE WALLET_TRANSFERS (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id          BIGINT UNSIGNED NOT NULL,
    from_wallet_id     BIGINT UNSIGNED NOT NULL,
    to_wallet_id       BIGINT UNSIGNED NOT NULL,
    amount             DECIMAL(18, 2)  NOT NULL,
    note               VARCHAR(255)    NULL,
    occurred_at        DATETIME        NOT NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    created_at         DATETIME        NOT NULL,
    CONSTRAINT chk_wallet_transfers_wallets CHECK (from_wallet_id <> to_wallet_id),
    CONSTRAINT fk_wallet_transfers_from_wallet FOREIGN KEY (from_wallet_id) REFERENCES WALLETS (id),
    CONSTRAINT fk_wallet_transfers_to_wallet FOREIGN KEY (to_wallet_id) REFERENCES WALLETS (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_wallet_transfers_family_id_occurred_at ON WALLET_TRANSFERS (family_id, occurred_at);
CREATE INDEX idx_wallet_transfers_from_wallet_id ON WALLET_TRANSFERS (from_wallet_id);
CREATE INDEX idx_wallet_transfers_to_wallet_id ON WALLET_TRANSFERS (to_wallet_id);
