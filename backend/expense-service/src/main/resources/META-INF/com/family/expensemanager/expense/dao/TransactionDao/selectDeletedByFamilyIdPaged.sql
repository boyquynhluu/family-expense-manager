SELECT
    id,
    wallet_id,
    category_id,
    refund_of_id,
    family_id,
    user_id,
    created_by_name,
    type,
    amount,
    occurred_at,
    note,
    is_private,
    receipt_path,
    receipt_content_type,
    deleted_at,
    deleted_by_user_id,
    deleted_by_name,
    version
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
    AND deleted_at IS NOT NULL
ORDER BY
    deleted_at DESC
LIMIT /* limit */20 OFFSET /* offset */0
