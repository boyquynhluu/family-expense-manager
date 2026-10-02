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
    deleted_at IS NOT NULL
    AND deleted_at < /* cutoff */'2026-01-01 00:00:00'
ORDER BY
    id
