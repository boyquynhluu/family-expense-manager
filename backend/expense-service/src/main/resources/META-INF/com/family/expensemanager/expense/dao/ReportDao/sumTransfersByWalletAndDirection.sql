SELECT
    wallet_id,
    direction,
    SUM(amount) AS total
FROM
    (
        SELECT to_wallet_id AS wallet_id, 'IN' AS direction, amount, occurred_at
        FROM WALLET_TRANSFERS
        WHERE family_id = /* familyId */0
        UNION ALL
        SELECT from_wallet_id AS wallet_id, 'OUT' AS direction, amount, occurred_at
        FROM WALLET_TRANSFERS
        WHERE family_id = /* familyId */0
    ) t
WHERE
/*%if fromDate != null */
    occurred_at >= /* fromDate */'2025-01-01' AND
/*%end*/
    occurred_at < /* toExclusive */'2025-02-01'
GROUP BY
    wallet_id, direction
