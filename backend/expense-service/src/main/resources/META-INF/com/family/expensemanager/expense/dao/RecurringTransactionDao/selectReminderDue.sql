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
    frequency,
    mode,
    remind_days_before,
    last_reminded_for,
    day_of_week,
    month_of_year,
    start_date,
    end_date,
    next_run_date,
    last_run_date,
    active,
    created_at
FROM
    RECURRING_TRANSACTIONS
WHERE
    active = 1
    AND type = 'EXPENSE'
    AND remind_days_before IS NOT NULL
    AND next_run_date > /* today */'2026-01-01'
    AND DATE_SUB(next_run_date, INTERVAL remind_days_before DAY) <= /* today */'2026-01-01'
    AND (last_reminded_for IS NULL OR last_reminded_for <> next_run_date)
