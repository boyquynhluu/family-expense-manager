-- A4 "nhắc và chờ xác nhận": a rule in CONFIRM mode does not record a transaction when due — it creates a
-- draft (RECURRING_DRAFTS) and notifies; a member types the real amount (an electricity bill varies) and
-- confirms it, or skips that occurrence. AUTO = the original behaviour.
-- C2 "nhắc hoá đơn": remind_days_before = notify that many days before next_run_date; last_reminded_for = the
-- next_run_date a reminder was already sent for (no duplicate when the scheduler runs twice).
ALTER TABLE RECURRING_TRANSACTIONS
    ADD COLUMN mode               VARCHAR(10) NOT NULL DEFAULT 'AUTO' AFTER frequency,
    ADD COLUMN remind_days_before TINYINT     NULL AFTER mode,
    ADD COLUMN last_reminded_for  DATE        NULL AFTER remind_days_before,
    ADD CONSTRAINT chk_recurring_transactions_mode CHECK (mode IN ('AUTO', 'CONFIRM')),
    ADD CONSTRAINT chk_recurring_transactions_remind CHECK (remind_days_before IS NULL OR remind_days_before BETWEEN 1 AND 30);

-- PENDING -> CONFIRMED (transaction_id = what it became) or SKIPPED. No FK to the rule: a draft stays as
-- history if the rule is deleted later.
CREATE TABLE RECURRING_DRAFTS (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id          BIGINT UNSIGNED NOT NULL,
    recurring_id       BIGINT UNSIGNED NOT NULL,
    wallet_id          BIGINT UNSIGNED NOT NULL,
    category_id        BIGINT UNSIGNED NOT NULL,
    type               VARCHAR(20)     NOT NULL,
    suggested_amount   DECIMAL(18, 2)  NOT NULL,
    note               VARCHAR(500)    NULL,
    due_date           DATE            NOT NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    transaction_id     BIGINT UNSIGNED NULL,
    decided_by_user_id BIGINT UNSIGNED NULL,
    decided_by_name    VARCHAR(100)    NULL,
    decided_at         DATETIME        NULL,
    created_at         DATETIME        NOT NULL,
    CONSTRAINT chk_recurring_drafts_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SKIPPED')),
    CONSTRAINT uk_recurring_drafts_occurrence UNIQUE (recurring_id, due_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_recurring_drafts_family_status ON RECURRING_DRAFTS (family_id, status, due_date);
