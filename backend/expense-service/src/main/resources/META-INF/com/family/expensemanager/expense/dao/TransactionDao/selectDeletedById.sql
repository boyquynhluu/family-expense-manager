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
    deleted_at,
    deleted_by_user_id,
    deleted_by_name,
    version
FROM
    TRANSACTIONS
WHERE
    id = /* id */0
    AND deleted_at IS NOT NULL
