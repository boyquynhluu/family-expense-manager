SELECT DISTINCT
    transaction_id
FROM
    TRANSACTION_SPLITS
WHERE
    category_id IN /* categoryIds */(1)
