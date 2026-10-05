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
    family_id = /* familyId */0
    AND deleted_at IS NOT NULL
ORDER BY
    id
