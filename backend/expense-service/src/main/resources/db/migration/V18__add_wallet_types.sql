-- C3 "Loại ví": what kind of money a wallet holds. Drives the negative-balance policy (A1): CASH, BANK and SAVINGS
-- may never go below 0, a CREDIT_CARD may go down to -credit_limit. Existing wallets become CASH.
--   CREDIT_CARD: credit_limit (required), statement_day / payment_due_day (day of month, for the due reminder, C2).
--   SAVINGS (term deposit): interest_rate (%/year) and maturity_date, informational.
-- last_payment_reminder_on: the due date a credit-card reminder was last sent for (no duplicate reminders).
ALTER TABLE WALLETS
    ADD COLUMN wallet_type              VARCHAR(20)    NOT NULL DEFAULT 'CASH' AFTER owner_user_id,
    ADD COLUMN credit_limit             DECIMAL(18, 2) NULL AFTER initial_balance,
    ADD COLUMN statement_day            TINYINT        NULL AFTER credit_limit,
    ADD COLUMN payment_due_day          TINYINT        NULL AFTER statement_day,
    ADD COLUMN interest_rate            DECIMAL(5, 2)  NULL AFTER payment_due_day,
    ADD COLUMN maturity_date            DATE           NULL AFTER interest_rate,
    ADD COLUMN last_payment_reminder_on DATE           NULL AFTER maturity_date,
    ADD CONSTRAINT chk_wallets_type CHECK (wallet_type IN ('CASH', 'BANK', 'CREDIT_CARD', 'SAVINGS')),
    ADD CONSTRAINT chk_wallets_statement_day CHECK (statement_day IS NULL OR statement_day BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_wallets_payment_due_day CHECK (payment_due_day IS NULL OR payment_due_day BETWEEN 1 AND 31);
