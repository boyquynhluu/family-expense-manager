-- Soft delete only recorded WHEN a transaction was deleted, not WHO did it — in a family where the OWNER can
-- delete anyone's transactions, the Trash page couldn't say who removed a row. The name is a snapshot at
-- delete time (same idea as created_by_name), so it still reads correctly after the member leaves/renames.
-- Both are cleared again by a restore.
ALTER TABLE TRANSACTIONS
    ADD COLUMN deleted_by_user_id BIGINT UNSIGNED NULL AFTER deleted_at,
    ADD COLUMN deleted_by_name    VARCHAR(100)    NULL AFTER deleted_by_user_id;
