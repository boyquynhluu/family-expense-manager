SELECT
    (SELECT COUNT(*) FROM LOANS WHERE wallet_id = /* walletId */0 OR counterparty_wallet_id = /* walletId */0)
        + (SELECT COUNT(*) FROM LOAN_PAYMENTS WHERE wallet_id = /* walletId */0)
