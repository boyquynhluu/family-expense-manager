SELECT
    id,
    family_id,
    from_wallet_id,
    to_wallet_id,
    amount,
    note,
    occurred_at,
    created_by_user_id,
    created_at
FROM
    WALLET_TRANSFERS
WHERE
    id = /* id */0
