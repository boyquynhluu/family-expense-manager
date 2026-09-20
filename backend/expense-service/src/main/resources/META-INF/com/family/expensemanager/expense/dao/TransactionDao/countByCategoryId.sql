SELECT
    COUNT(*)
FROM
    TRANSACTIONS
WHERE
    category_id = /* categoryId */0
    AND deleted_at IS NULL
