SELECT
    COALESCE(SUM(amount), 0)
FROM
    TRANSACTIONS
WHERE
    wallet_id = /* walletId */0
    AND type = /* type */'EXPENSE'
    AND deleted_at IS NULL
    AND occurred_at < /* cutoff */'2026-10-01 00:00:00'
