SELECT
    COUNT(*)
FROM
    TRANSACTIONS
WHERE
    wallet_id = /* walletId */0
    AND deleted_at IS NULL
