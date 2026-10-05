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
ORDER BY
    period_month DESC, id
LIMIT /* limit */20 OFFSET /* offset */0
