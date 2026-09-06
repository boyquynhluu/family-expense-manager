SELECT
    id,
    family_id,
    category_id,
    period_month,
    limit_amount
FROM
    BUDGETS
WHERE
    family_id = /* familyId */0
ORDER BY
    period_month DESC, id
