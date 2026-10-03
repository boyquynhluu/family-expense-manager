SELECT
    wallet_id,
    type,
    SUM(amount) AS total
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
/*%if fromDate != null */
    AND occurred_at >= /* fromDate */'2025-01-01'
/*%end*/
    AND occurred_at < /* toExclusive */'2025-02-01'
    AND deleted_at IS NULL
GROUP BY
    wallet_id, type
