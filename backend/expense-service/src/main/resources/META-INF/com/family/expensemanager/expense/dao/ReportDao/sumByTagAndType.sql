SELECT
    g.id AS tag_id,
    g.name,
    t.type,
    SUM(t.amount) AS total
FROM
    TRANSACTIONS t
        JOIN TRANSACTION_TAGS tt ON tt.transaction_id = t.id
        JOIN TAGS g ON g.id = tt.tag_id
WHERE
    t.family_id = /* familyId */0
    AND t.occurred_at >= /* fromDate */'2025-01-01'
    AND t.occurred_at < /* toExclusive */'2025-02-01'
    AND t.deleted_at IS NULL
GROUP BY
    g.id, g.name, t.type
ORDER BY
    g.name
