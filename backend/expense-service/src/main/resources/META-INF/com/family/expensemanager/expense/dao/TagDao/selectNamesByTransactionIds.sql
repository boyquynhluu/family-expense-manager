SELECT
    tt.transaction_id,
    t.name
FROM
    TRANSACTION_TAGS tt
        JOIN TAGS t ON t.id = tt.tag_id
WHERE
    tt.transaction_id IN /* transactionIds */(1)
ORDER BY
    t.name
