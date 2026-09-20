SELECT
    DATE_FORMAT(occurred_at, '%Y-%m-%d') AS bucket,
    type,
    SUM(amount) AS total
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
    AND occurred_at >= /* fromDate */'2025-01-01'
    AND occurred_at < /* toExclusive */'2025-02-01'
    AND deleted_at IS NULL
GROUP BY
    DATE_FORMAT(occurred_at, '%Y-%m-%d'), type
