SELECT
    id,
    loan_id,
    family_id,
    wallet_id,
    amount,
    paid_at,
    note,
    created_by_user_id,
    created_by_name,
    created_at
FROM
    LOAN_PAYMENTS
WHERE
    id = /* id */0
