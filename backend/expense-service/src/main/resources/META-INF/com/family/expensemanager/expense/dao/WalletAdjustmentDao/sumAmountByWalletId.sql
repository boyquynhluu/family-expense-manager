SELECT
    COALESCE(SUM(amount), 0)
FROM
    WALLET_ADJUSTMENTS
WHERE
    wallet_id = /* walletId */0
