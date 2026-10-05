SELECT
    id,
    family_id,
    wallet_id,
    amount,
    balance_before,
    balance_after,
    note,
    occurred_at,
    created_by_user_id,
    created_by_name,
    created_at
FROM
    WALLET_ADJUSTMENTS
WHERE
    id = /* id */0
