SELECT DISTINCT
    family_id
FROM
    WALLETS
WHERE
    deleted_at IS NULL
ORDER BY
    family_id
