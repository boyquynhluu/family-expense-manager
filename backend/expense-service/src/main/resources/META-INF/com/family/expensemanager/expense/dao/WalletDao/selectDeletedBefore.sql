SELECT
    id,
    family_id,
    owner_user_id,
    name,
    currency,
    initial_balance,
    wallet_type,
    credit_limit,
    statement_day,
    payment_due_day,
    interest_rate,
    maturity_date,
    last_payment_reminder_on,
    deleted_at
FROM
    WALLETS
WHERE
    deleted_at IS NOT NULL
    AND deleted_at < /* cutoff */'2026-01-01 00:00:00'
ORDER BY
    id
