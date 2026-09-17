-- Templates for bills that repeat every month (rent, internet, subscriptions — see
-- README "3. Không có giao dịch định kỳ"). A daily job (RecurringTransactionScheduler)
-- turns each due row into a real TRANSACTIONS row via TransactionService.create(), so
-- every side effect (budget-crossing email, cache eviction, EXPENSE_CREATED event)
-- fires exactly the same as a manually entered transaction.
CREATE TABLE RECURRING_TRANSACTIONS (
    id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id                BIGINT UNSIGNED NOT NULL,
    wallet_id                BIGINT UNSIGNED NOT NULL,
    category_id              BIGINT UNSIGNED NOT NULL,
    created_by_user_id       BIGINT UNSIGNED NOT NULL,
    -- Snapshotted at creation time, same reasoning as ExpenseEvent's userEmail/
    -- userDisplayName: this service has no live access to auth-service's USERS table,
    -- and the scheduler runs outside any request's JWT, so there is no CurrentUser to
    -- read from when a rule fires.
    created_by_email         VARCHAR(255)    NOT NULL,
    created_by_display_name  VARCHAR(255)    NOT NULL,
    type                     VARCHAR(20)     NOT NULL,
    amount                   DECIMAL(18, 2)  NOT NULL,
    note                     VARCHAR(500)    NULL,
    day_of_month             TINYINT UNSIGNED NOT NULL,
    start_date               DATE            NOT NULL,
    end_date                 DATE            NULL,
    next_run_date            DATE            NOT NULL,
    active                   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_recurring_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_recurring_transactions_day_of_month CHECK (day_of_month BETWEEN 1 AND 31),
    CONSTRAINT fk_recurring_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES WALLETS (id),
    CONSTRAINT fk_recurring_transactions_category FOREIGN KEY (category_id) REFERENCES CATEGORIES (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_recurring_transactions_family_id ON RECURRING_TRANSACTIONS (family_id);
CREATE INDEX idx_recurring_transactions_wallet_id ON RECURRING_TRANSACTIONS (wallet_id);
CREATE INDEX idx_recurring_transactions_category_id ON RECURRING_TRANSACTIONS (category_id);
CREATE INDEX idx_recurring_transactions_due ON RECURRING_TRANSACTIONS (active, next_run_date);
