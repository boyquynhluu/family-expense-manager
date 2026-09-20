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
    AND period_month = /* periodMonth */'2025-01'
ORDER BY
    id
