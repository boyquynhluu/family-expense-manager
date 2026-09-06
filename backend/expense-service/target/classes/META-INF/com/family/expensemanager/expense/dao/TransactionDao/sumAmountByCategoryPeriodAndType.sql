SELECT
    COALESCE(SUM(amount), 0)
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
    AND category_id = /* categoryId */0
    AND DATE_FORMAT(occurred_at, '%Y-%m') = /* periodMonth */'2025-01'
    AND type = /* type */'EXPENSE'
