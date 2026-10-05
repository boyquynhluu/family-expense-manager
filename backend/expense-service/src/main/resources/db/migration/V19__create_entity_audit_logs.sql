-- A3: who created/changed/deleted a wallet, a budget or a transfer, and what it looked like before and after —
-- the same idea as TRANSACTION_AUDIT_LOGS (V13) for the other money-relevant entities. No foreign key on
-- entity_id: the history must outlive the row (a hard-deleted transfer, a purged wallet).
CREATE TABLE ENTITY_AUDIT_LOGS (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id     BIGINT UNSIGNED NOT NULL,
    entity_type   VARCHAR(20)     NOT NULL,
    entity_id     BIGINT UNSIGNED NOT NULL,
    action        VARCHAR(20)     NOT NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    actor_name    VARCHAR(100)    NULL,
    before_json   JSON            NULL,
    after_json    JSON            NULL,
    created_at    DATETIME        NOT NULL,
    CONSTRAINT chk_entity_audit_logs_type CHECK (entity_type IN ('WALLET', 'BUDGET', 'TRANSFER'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_entity_audit_logs_entity ON ENTITY_AUDIT_LOGS (family_id, entity_type, entity_id, id);
CREATE INDEX idx_entity_audit_logs_family_created ON ENTITY_AUDIT_LOGS (family_id, created_at);
