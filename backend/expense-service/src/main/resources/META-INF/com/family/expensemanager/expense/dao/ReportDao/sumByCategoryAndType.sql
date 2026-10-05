SELECT
    category_id,
    type,
    SUM(amount) AS total
FROM
    TRANSACTION_CATEGORY_LINES
WHERE
    family_id = /* familyId */0
    AND occurred_at >= /* fromDate */'2025-01-01'
    AND occurred_at < /* toExclusive */'2025-02-01'
    AND deleted_at IS NULL
GROUP BY
    category_id, type
