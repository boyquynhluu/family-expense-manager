SELECT
    id,
    family_id,
    recurring_id,
    wallet_id,
    category_id,
    type,
    suggested_amount,
    note,
    due_date,
    created_by_user_id,
    status,
    transaction_id,
    decided_by_user_id,
    decided_by_name,
    decided_at,
    created_at
FROM
    RECURRING_DRAFTS
WHERE
    family_id = /* familyId */0
    AND status = 'PENDING'
ORDER BY
    due_date, id
LIMIT /* limit */20 OFFSET /* offset */0
