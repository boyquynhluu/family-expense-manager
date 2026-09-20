SELECT
    id,
    wallet_id,
    category_id,
    family_id,
    user_id,
    type,
    amount,
    occurred_at,
    note,
    receipt_path,
    receipt_content_type,
    deleted_at
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
    AND deleted_at IS NULL
/*%if walletId != null */
    AND wallet_id = /* walletId */0
/*%end*/
/*%if categoryId != null */
    AND category_id = /* categoryId */0
/*%end*/
/*%if type != null */
    AND type = /* type */'EXPENSE'
/*%end*/
/*%if fromDate != null */
    AND DATE(occurred_at) >= /* fromDate */'2025-01-01'
/*%end*/
/*%if toDate != null */
    AND DATE(occurred_at) <= /* toDate */'2025-01-31'
/*%end*/
ORDER BY
    occurred_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
