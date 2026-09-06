CREATE TABLE WALLETS (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id       BIGINT UNSIGNED NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    currency        CHAR(3)         NOT NULL DEFAULT 'VND',
    initial_balance DECIMAL(18, 2)  NOT NULL DEFAULT 0
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_wallets_family_id ON WALLETS (family_id);

CREATE TABLE CATEGORIES (
    id        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id BIGINT UNSIGNED NOT NULL,
    name      VARCHAR(255)    NOT NULL,
    type      VARCHAR(20)     NOT NULL,
    icon      VARCHAR(50)     NULL,
    color     VARCHAR(7)      NULL,
    CONSTRAINT chk_categories_type CHECK (type IN ('INCOME', 'EXPENSE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_categories_family_id ON CATEGORIES (family_id);

CREATE TABLE TRANSACTIONS (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    wallet_id   BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    family_id   BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(20)     NOT NULL,
    amount      DECIMAL(18, 2)  NOT NULL,
    occurred_at DATETIME        NOT NULL,
    note        VARCHAR(500)    NULL,
    CONSTRAINT chk_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT fk_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES WALLETS (id),
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES CATEGORIES (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transactions_wallet_id ON TRANSACTIONS (wallet_id);
CREATE INDEX idx_transactions_category_id ON TRANSACTIONS (category_id);
CREATE INDEX idx_transactions_family_id_occurred_at ON TRANSACTIONS (family_id, occurred_at);

CREATE TABLE BUDGETS (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id    BIGINT UNSIGNED NOT NULL,
    category_id  BIGINT UNSIGNED NOT NULL,
    period_month CHAR(7)         NOT NULL,
    limit_amount DECIMAL(18, 2)  NOT NULL,
    CONSTRAINT uk_budgets_category_period UNIQUE (category_id, period_month),
    CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES CATEGORIES (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_budgets_family_id ON BUDGETS (family_id);
