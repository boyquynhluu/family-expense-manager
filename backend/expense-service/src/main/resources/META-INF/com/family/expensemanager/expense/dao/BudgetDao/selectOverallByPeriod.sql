SELECT
    id,
    family_id,
    category_id,
    period_month,
    limit_amount,
    version
FROM
    BUDGETS
WHERE
    family_id = /* familyId */0
    AND category_id IS NULL
    AND period_month = /* periodMonth */'2025-01'
