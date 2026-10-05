SELECT
    COALESCE(-SUM(amount), 0)
FROM
    TRANSACTIONS
WHERE
    refund_of_id = /* originalId */0
    AND deleted_at IS NULL
