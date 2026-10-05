SELECT
    COALESCE(SUM(amount), 0)
FROM
    LOAN_PAYMENTS
WHERE
    loan_id = /* loanId */0
