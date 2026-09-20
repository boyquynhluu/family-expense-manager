SELECT
    COALESCE(SUM(amount), 0)
FROM
    WALLET_TRANSFERS
WHERE
    to_wallet_id = /* walletId */0
