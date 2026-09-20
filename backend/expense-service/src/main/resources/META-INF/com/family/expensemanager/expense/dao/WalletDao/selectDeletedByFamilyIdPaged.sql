SELECT
    id,
    family_id,
    name,
    currency,
    initial_balance,
    deleted_at
FROM
    WALLETS
WHERE
    family_id = /* familyId */0
    AND deleted_at IS NOT NULL
ORDER BY
    deleted_at DESC
LIMIT /* limit */20 OFFSET /* offset */0
