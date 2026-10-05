SELECT
    COUNT(*)
FROM
    TRANSACTIONS
WHERE
    refund_of_id = /* originalId */0
    AND deleted_at IS NULL
