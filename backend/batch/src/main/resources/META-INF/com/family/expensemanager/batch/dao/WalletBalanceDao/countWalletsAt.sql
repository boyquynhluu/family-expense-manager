SELECT
    COUNT(*)
FROM
    WALLETS
WHERE
    deleted_at IS NULL
    OR deleted_at >= /* cutoff */'2026-10-01 00:00:00'
