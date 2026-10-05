SELECT
    wallet_id,
    SUM(flow) AS total
FROM
    (
        SELECT wallet_id, start_date AS at_date,
               CASE direction WHEN 'BORROWED' THEN principal ELSE -principal END AS flow
        FROM LOANS
        WHERE family_id = /* familyId */0
        UNION ALL
        SELECT counterparty_wallet_id AS wallet_id, start_date AS at_date,
               CASE direction WHEN 'BORROWED' THEN -principal ELSE principal END AS flow
        FROM LOANS
        WHERE family_id = /* familyId */0
          AND counterparty_wallet_id IS NOT NULL
        UNION ALL
        SELECT p.wallet_id, p.paid_at AS at_date,
               CASE l.direction WHEN 'LENT' THEN p.amount ELSE -p.amount END AS flow
        FROM LOAN_PAYMENTS p
                 JOIN LOANS l ON l.id = p.loan_id
        WHERE p.family_id = /* familyId */0
        UNION ALL
        SELECT l.counterparty_wallet_id AS wallet_id, p.paid_at AS at_date,
               CASE l.direction WHEN 'BORROWED' THEN p.amount ELSE -p.amount END AS flow
        FROM LOAN_PAYMENTS p
                 JOIN LOANS l ON l.id = p.loan_id
        WHERE p.family_id = /* familyId */0
          AND l.counterparty_wallet_id IS NOT NULL
    ) t
WHERE
/*%if fromDate != null */
    at_date >= /* fromDate */'2025-01-01' AND
/*%end*/
    at_date < /* toExclusive */'2025-02-01'
GROUP BY
    wallet_id
