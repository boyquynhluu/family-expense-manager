SELECT
    id,
    wallet_id,
    category_id,
    family_id,
    user_id,
    type,
    amount,
    occurred_at,
    note
FROM
    TRANSACTIONS
WHERE
    id = /* id */0
