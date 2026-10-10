-- SHA-256 of the request body an Idempotency-Key was first used with (see IdempotencyGuard): a retry must
-- carry the same body, so reusing a key for a DIFFERENT request (e.g. the amount was edited before re-saving)
-- is refused with 422 instead of silently replaying the first response. NULL for rows from before this column.
ALTER TABLE IDEMPOTENCY_KEYS ADD COLUMN request_hash CHAR(64) NULL AFTER idempotency_key;
