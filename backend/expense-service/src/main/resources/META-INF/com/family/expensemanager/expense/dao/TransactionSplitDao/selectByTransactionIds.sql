SELECT
    id,
    transaction_id,
    category_id,
    amount
FROM
    TRANSACTION_SPLITS
WHERE
    transaction_id IN /* transactionIds */(1)
ORDER BY
    transaction_id, id
