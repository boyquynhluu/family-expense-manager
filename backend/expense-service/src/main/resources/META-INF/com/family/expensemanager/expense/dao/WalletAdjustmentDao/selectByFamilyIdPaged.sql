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
    family_id = /* familyId */0
/*%if walletId != null */
    AND wallet_id = /* walletId */0
/*%end*/
ORDER BY
    occurred_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
