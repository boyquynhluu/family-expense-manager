-- B3, loans INSIDE the family: the other side is one of the family's own wallets (counterparty_wallet_id) instead
-- of an outside person. Money then really moves between the two wallets — the lender's wallet goes down and the
-- borrower's up, each repayment the other way round — so the family's total is unchanged. NULL = an outside
-- counterparty (counterparty_name only), as before.
ALTER TABLE LOANS
    ADD COLUMN counterparty_wallet_id BIGINT UNSIGNED NULL AFTER counterparty_contact,
    ADD CONSTRAINT fk_loans_counterparty_wallet FOREIGN KEY (counterparty_wallet_id) REFERENCES WALLETS (id),
    ADD CONSTRAINT chk_loans_counterparty_wallet CHECK (counterparty_wallet_id IS NULL OR counterparty_wallet_id <> wallet_id);
