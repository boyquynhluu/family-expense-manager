SELECT
    id,
    family_id,
    category_id,
    period_month,
    limit_amount
FROM
    BUDGETS
WHERE
    category_id = /* categoryId */0
    AND period_month = /* periodMonth */'2025-01'
