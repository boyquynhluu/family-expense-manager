-- A6: a budget can be scoped to one wallet and/or one member (NULL = all), cover a YEAR instead of a month
-- (period_month then holds just "yyyy"), and roll its unspent remainder over to the next period.
-- The old UNIQUE (category_id, period_month) no longer describes a duplicate (the scope widened); BudgetService
-- checks duplicates per full scope. The FK on category_id needs an index of its own before the unique key goes.
CREATE INDEX idx_budgets_category_id ON BUDGETS (category_id);
ALTER TABLE BUDGETS DROP INDEX uk_budgets_category_period;
ALTER TABLE BUDGETS
    ADD COLUMN wallet_id   BIGINT UNSIGNED NULL AFTER category_id,
    ADD COLUMN user_id     BIGINT UNSIGNED NULL AFTER wallet_id,
    ADD COLUMN period_type VARCHAR(5)      NOT NULL DEFAULT 'MONTH' AFTER user_id,
    ADD COLUMN rollover    BOOLEAN         NOT NULL DEFAULT FALSE AFTER limit_amount,
    ADD CONSTRAINT chk_budgets_period_type CHECK (period_type IN ('MONTH', 'YEAR')),
    ADD CONSTRAINT fk_budgets_wallet FOREIGN KEY (wallet_id) REFERENCES WALLETS (id);
CREATE INDEX idx_budgets_family_period ON BUDGETS (family_id, period_month);
