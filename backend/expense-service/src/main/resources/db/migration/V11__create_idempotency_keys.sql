-- Lets a client safely retry POST /transactions and POST /transfers (e.g. after a timeout, not knowing
-- whether the first attempt landed) by sending the same Idempotency-Key header: a retried request with
-- a key that already succeeded gets back the SAME response instead of creating a second row. See
-- IdempotencyGuard for how this table is used.
CREATE TABLE IDEMPOTENCY_KEYS (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    family_id        BIGINT UNSIGNED NOT NULL,
    scope            VARCHAR(50)     NOT NULL,
    idempotency_key  VARCHAR(255)    NOT NULL,
    -- NULL while the original request is still in flight; filled in once it succeeds.
    response_json    JSON            NULL,
    created_at       DATETIME        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_keys UNIQUE (family_id, scope, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_created_at ON IDEMPOTENCY_KEYS (created_at);
