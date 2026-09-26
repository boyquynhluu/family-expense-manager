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
    id = /* id */0
    AND deleted_at IS NULL
FOR UPDATE
