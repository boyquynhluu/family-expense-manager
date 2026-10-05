SELECT
    wallet_id,
    SUM(amount) AS total
FROM
    WALLET_ADJUSTMENTS
WHERE
    family_id = /* familyId */0
/*%if fromDate != null */
    AND occurred_at >= /* fromDate */'2025-01-01'
/*%end*/
    AND occurred_at < /* toExclusive */'2025-02-01'
GROUP BY
    wallet_id
