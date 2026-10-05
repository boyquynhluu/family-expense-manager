-- B3 "Vay / cho vay / nợ". direction BORROWED = the family borrowed (money came INTO wallet_id), LENT = the
-- family lent (money went OUT of wallet_id). Repayments are LOAN_PAYMENTS rows, each through a wallet (out of it
-- for BORROWED, into it for LENT). None of this is income or expense — it only moves wallet balances.
-- remaining = principal - SUM(payments); status turns CLOSED when it reaches 0.
-- last_reminded_for: the due_date a "sắp đến hạn" reminder was sent for.
CREATE TABLE LOANS (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id            BIGINT UNSIGNED NOT NULL,
    direction            VARCHAR(10)     NOT NULL,
    counterparty_name    VARCHAR(100)    NOT NULL,
    counterparty_contact VARCHAR(100)    NULL,
    principal            DECIMAL(18, 2)  NOT NULL,
    wallet_id            BIGINT UNSIGNED NOT NULL,
    start_date           DATE            NOT NULL,
    due_date             DATE            NULL,
    note                 VARCHAR(255)    NULL,
    status               VARCHAR(10)     NOT NULL DEFAULT 'OPEN',
    last_reminded_for    DATE            NULL,
    created_by_user_id   BIGINT UNSIGNED NOT NULL,
    created_by_name      VARCHAR(100)    NULL,
    created_at           DATETIME        NOT NULL,
    CONSTRAINT chk_loans_direction CHECK (direction IN ('BORROWED', 'LENT')),
    CONSTRAINT chk_loans_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_loans_principal CHECK (principal > 0),
    CONSTRAINT fk_loans_wallet FOREIGN KEY (wallet_id) REFERENCES WALLETS (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_loans_family_status ON LOANS (family_id, status);

CREATE TABLE LOAN_PAYMENTS (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    loan_id            BIGINT UNSIGNED NOT NULL,
    family_id          BIGINT UNSIGNED NOT NULL,
    wallet_id          BIGINT UNSIGNED NOT NULL,
    amount             DECIMAL(18, 2)  NOT NULL,
    paid_at            DATETIME        NOT NULL,
    note               VARCHAR(255)    NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    created_by_name    VARCHAR(100)    NULL,
    created_at         DATETIME        NOT NULL,
    CONSTRAINT chk_loan_payments_amount CHECK (amount > 0),
    CONSTRAINT fk_loan_payments_loan FOREIGN KEY (loan_id) REFERENCES LOANS (id) ON DELETE CASCADE,
    CONSTRAINT fk_loan_payments_wallet FOREIGN KEY (wallet_id) REFERENCES WALLETS (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_loan_payments_loan ON LOAN_PAYMENTS (loan_id);
CREATE INDEX idx_loan_payments_wallet ON LOAN_PAYMENTS (wallet_id);
