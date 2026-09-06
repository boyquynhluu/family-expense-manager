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
    family_id = /* familyId */0
ORDER BY
    occurred_at DESC, id DESC
