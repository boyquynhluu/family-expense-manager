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
    AND deleted_at IS NULL
ORDER BY
    id
