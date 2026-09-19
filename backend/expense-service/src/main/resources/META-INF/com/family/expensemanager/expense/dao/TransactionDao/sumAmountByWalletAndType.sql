SELECT
    COALESCE(SUM(amount), 0)
FROM
    TRANSACTIONS
WHERE
    wallet_id = /* walletId */0
    AND type = /* type */'EXPENSE'
    AND deleted_at IS NULL
