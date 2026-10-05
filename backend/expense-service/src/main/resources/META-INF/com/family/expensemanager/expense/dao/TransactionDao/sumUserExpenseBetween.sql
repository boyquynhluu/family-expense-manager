SELECT
    COALESCE(SUM(amount), 0)
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
    AND user_id = /* userId */0
    AND type = 'EXPENSE'
    AND occurred_at >= /* fromDate */'2026-01-01'
    AND occurred_at < /* toExclusive */'2026-01-02'
    AND deleted_at IS NULL
/*%if excludeId != null */
    AND id <> /* excludeId */0
/*%end*/
