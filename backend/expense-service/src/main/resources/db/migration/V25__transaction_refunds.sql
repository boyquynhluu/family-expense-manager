-- C4 "Hoàn tiền / trả hàng": a refund is an EXPENSE row with a NEGATIVE amount pointing at the expense it
-- refunds (refund_of_id), in the same category. Every existing SUM(amount) of EXPENSE then nets it out by
-- itself — category reports, budgets, wallet balances — instead of a fake INCOME inflating both income and expense.
ALTER TABLE TRANSACTIONS
    ADD COLUMN refund_of_id BIGINT UNSIGNED NULL AFTER category_id,
    ADD CONSTRAINT fk_transactions_refund_of FOREIGN KEY (refund_of_id) REFERENCES TRANSACTIONS (id),
    ADD CONSTRAINT chk_transactions_refund_sign CHECK (
        (refund_of_id IS NULL AND amount > 0) OR (refund_of_id IS NOT NULL AND amount < 0 AND type = 'EXPENSE'));
