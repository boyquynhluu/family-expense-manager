SELECT
    t.user_id,
    t.created_by_name AS display_name
FROM
    TRANSACTIONS t
WHERE
    t.id IN (
        SELECT
            MAX(t2.id)
        FROM
            TRANSACTIONS t2
        WHERE
            t2.family_id = /* familyId */0
            AND t2.occurred_at >= /* fromDate */'2025-01-01'
            AND t2.occurred_at < /* toExclusive */'2025-02-01'
            AND t2.deleted_at IS NULL
            AND t2.created_by_name IS NOT NULL
        GROUP BY
            t2.user_id
    )
