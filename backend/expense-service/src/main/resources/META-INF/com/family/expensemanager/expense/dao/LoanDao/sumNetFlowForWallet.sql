SELECT
    COALESCE(SUM(flow), 0)
FROM
    (
        -- the loan's own wallet: borrowed money came in, lent money went out
        SELECT CASE direction WHEN 'BORROWED' THEN principal ELSE -principal END AS flow
        FROM LOANS
        WHERE wallet_id = /* walletId */0
        UNION ALL
        -- a family wallet on the other side mirrors it
        SELECT CASE direction WHEN 'BORROWED' THEN -principal ELSE principal END AS flow
        FROM LOANS
        WHERE counterparty_wallet_id = /* walletId */0
        UNION ALL
        -- repayments through this wallet: repaying a debt goes out, collecting comes in
        SELECT CASE l.direction WHEN 'LENT' THEN p.amount ELSE -p.amount END AS flow
        FROM LOAN_PAYMENTS p
                 JOIN LOANS l ON l.id = p.loan_id
        WHERE p.wallet_id = /* walletId */0
        UNION ALL
        -- ...and land on (or leave) the family wallet on the other side
        SELECT CASE l.direction WHEN 'BORROWED' THEN p.amount ELSE -p.amount END AS flow
        FROM LOAN_PAYMENTS p
                 JOIN LOANS l ON l.id = p.loan_id
        WHERE l.counterparty_wallet_id = /* walletId */0
    ) t
