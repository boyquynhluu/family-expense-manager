-- A5: finer roles than OWNER/MEMBER (VIEWER = read only, CHILD = limited spending) live in auth-service; the
-- money rules live here.
-- MEMBER_SPENDING_LIMITS: a member's maximum EXPENSE per day / per month (NULL = no limit), set by the OWNER —
-- meant for a CHILD, but applies to whoever has a row.
CREATE TABLE MEMBER_SPENDING_LIMITS (
    family_id     BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    daily_limit   DECIMAL(18, 2)  NULL,
    monthly_limit DECIMAL(18, 2)  NULL,
    updated_at    DATETIME        NOT NULL,
    PRIMARY KEY (family_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- FAMILY_SETTINGS.approval_threshold: an EXPENSE above it, entered by anyone but the OWNER, waits in
-- TRANSACTION_APPROVALS (not a transaction yet — so it touches no balance, budget or report) until the OWNER
-- approves it (then it is recorded as a normal transaction) or rejects it. NULL = no approval needed.
CREATE TABLE FAMILY_SETTINGS (
    family_id          BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    approval_threshold DECIMAL(18, 2)  NULL,
    updated_at         DATETIME        NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE TRANSACTION_APPROVALS (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id          BIGINT UNSIGNED NOT NULL,
    requester_user_id  BIGINT UNSIGNED NOT NULL,
    requester_name     VARCHAR(100)    NULL,
    requester_email    VARCHAR(255)    NULL,
    wallet_id          BIGINT UNSIGNED NOT NULL,
    category_id        BIGINT UNSIGNED NOT NULL,
    amount             DECIMAL(18, 2)  NOT NULL,
    occurred_at        DATETIME        NOT NULL,
    note               VARCHAR(500)    NULL,
    is_private         BOOLEAN         NOT NULL DEFAULT FALSE,
    status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    decided_by_user_id BIGINT UNSIGNED NULL,
    decided_by_name    VARCHAR(100)    NULL,
    decided_at         DATETIME        NULL,
    reject_reason      VARCHAR(255)    NULL,
    transaction_id     BIGINT UNSIGNED NULL,
    created_at         DATETIME        NOT NULL,
    CONSTRAINT chk_transaction_approvals_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transaction_approvals_family_status ON TRANSACTION_APPROVALS (family_id, status, created_at);
CREATE INDEX idx_transaction_approvals_requester ON TRANSACTION_APPROVALS (family_id, requester_user_id);
