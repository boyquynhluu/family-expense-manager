SELECT
    id,
    family_id,
    wallet_id,
    category_id,
    created_by_user_id,
    created_by_email,
    created_by_display_name,
    type,
    amount,
    note,
    day_of_month,
    start_date,
    end_date,
    next_run_date,
    last_run_date,
    active,
    created_at
FROM
    RECURRING_TRANSACTIONS
WHERE
    active = TRUE
    AND next_run_date <= /* today */'2025-01-01'
ORDER BY
    id
