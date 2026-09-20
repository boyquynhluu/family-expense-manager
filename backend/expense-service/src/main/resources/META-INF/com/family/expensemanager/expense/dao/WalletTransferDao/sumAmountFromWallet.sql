SELECT
    COALESCE(SUM(amount), 0)
FROM
    WALLET_TRANSFERS
WHERE
    from_wallet_id = /* walletId */0
