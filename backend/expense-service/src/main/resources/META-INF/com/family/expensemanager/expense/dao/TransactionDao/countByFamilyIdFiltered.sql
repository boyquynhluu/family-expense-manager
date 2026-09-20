SELECT
    COUNT(*)
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
/*%if notePattern != null */
    AND LOWER(note) LIKE /* notePattern */'%abc%' ESCAPE '!'
/*%end*/
/*%if minAmount != null */
    AND amount >= /* minAmount */0
/*%end*/
/*%if maxAmount != null */
    AND amount <= /* maxAmount */0
/*%end*/
