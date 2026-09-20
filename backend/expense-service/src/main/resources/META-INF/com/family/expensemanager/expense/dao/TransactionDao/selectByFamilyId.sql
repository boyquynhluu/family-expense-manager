SELECT
    id,
    wallet_id,
    category_id,
    family_id,
    user_id,
    created_by_name,
    type,
    amount,
    occurred_at,
    note,
    receipt_path,
    receipt_content_type,
    deleted_at
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
    AND deleted_at IS NULL
ORDER BY
    occurred_at DESC, id DESC
