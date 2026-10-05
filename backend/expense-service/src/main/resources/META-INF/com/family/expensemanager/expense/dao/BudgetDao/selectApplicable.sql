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
    family_id = /* familyId */0
    AND ((period_type = 'MONTH' AND period_month = /* periodMonth */'2026-01')
        OR (period_type = 'YEAR' AND period_month = /* periodYear */'2026'))
ORDER BY
    period_type, id
