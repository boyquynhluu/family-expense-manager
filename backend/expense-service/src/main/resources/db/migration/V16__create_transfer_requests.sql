-- "Yêu cầu chuyển tiền": member B asks the owner of wallet A to move money from A into B's own wallet.
-- PENDING (đang chờ) → COMPLETED (đã chuyển, transfer_id = the WALLET_TRANSFERS row it created)
--                    → REJECTED  (từ chối).
-- approver_user_id is wallet A's owner when the request was made (who gets the email and may decide).
-- No foreign keys to WALLETS: a request is history and must not block deleting/purging a wallet later.
CREATE TABLE TRANSFER_REQUESTS (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    family_id           BIGINT UNSIGNED NOT NULL,
    requester_user_id   BIGINT UNSIGNED NOT NULL,
    requester_name      VARCHAR(100)    NULL,
    approver_user_id    BIGINT UNSIGNED NOT NULL,
    from_wallet_id      BIGINT UNSIGNED NOT NULL,
    to_wallet_id        BIGINT UNSIGNED NOT NULL,
    amount              DECIMAL(18, 2)  NOT NULL,
    note                VARCHAR(255)    NULL,
    status              VARCHAR(20)     NOT NULL,
    decided_by_user_id  BIGINT UNSIGNED NULL,
    decided_by_name     VARCHAR(100)    NULL,
    decided_at          DATETIME        NULL,
    transfer_id         BIGINT UNSIGNED NULL,
    created_at          DATETIME        NOT NULL,
    CONSTRAINT chk_transfer_requests_wallets CHECK (from_wallet_id <> to_wallet_id),
    CONSTRAINT chk_transfer_requests_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transfer_requests_family_requester ON TRANSFER_REQUESTS (family_id, requester_user_id);
CREATE INDEX idx_transfer_requests_family_approver ON TRANSFER_REQUESTS (family_id, approver_user_id, status);
