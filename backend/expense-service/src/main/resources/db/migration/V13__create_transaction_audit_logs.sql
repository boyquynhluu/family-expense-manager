-- Append-only history of every change to a transaction: who did what (CREATED / UPDATED / DELETED /
-- RESTORED), when, and the transaction's state before and after. Written in the same DB transaction as the
-- change itself (see TransactionAuditService), so a change that commits always has its log row.
-- No foreign key to TRANSACTIONS on purpose: the history must outlive the row it describes.
CREATE TABLE TRANSACTION_AUDIT_LOGS (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id       BIGINT UNSIGNED NOT NULL,
    transaction_id  BIGINT UNSIGNED NOT NULL,
    action          VARCHAR(20)     NOT NULL,
    actor_user_id   BIGINT UNSIGNED NULL,
    actor_name      VARCHAR(100)    NULL,
    -- NULL for CREATED (nothing before) and DELETED (nothing after).
    before_json     JSON            NULL,
    after_json      JSON            NULL,
    created_at      DATETIME        NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transaction_audit_logs_transaction ON TRANSACTION_AUDIT_LOGS (transaction_id, id);
CREATE INDEX idx_transaction_audit_logs_family ON TRANSACTION_AUDIT_LOGS (family_id, created_at);
