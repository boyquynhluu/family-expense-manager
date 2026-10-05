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
    recurring_id = /* recurringId */0
    AND due_date = /* dueDate */'2026-01-01'
