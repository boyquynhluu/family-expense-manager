SELECT
    id,
    family_id,
    category_id,
    wallet_id,
    user_id,
    period_type,
    period_month,
    limit_amount,
    rollover,
    version
FROM
    BUDGETS
WHERE
    id = /* id */0
