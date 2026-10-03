-- A private transaction is visible (list, detail, receipt, history, trash, export) only to the member who
-- created it — not even to the family OWNER. Its AMOUNT still counts in every aggregate (wallet balances,
-- summaries, reports, budgets), so family totals stay true to the real money; only the details are hidden.
ALTER TABLE TRANSACTIONS
    ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE AFTER note;
