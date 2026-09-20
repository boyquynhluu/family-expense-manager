SELECT
    id,
    wallet_id,
    category_id,
    family_id,
    user_id,
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
    id = /* id */0
    AND deleted_at IS NOT NULL
