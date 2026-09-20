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
    family_id = /* familyId */0
ORDER BY
    occurred_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
