SELECT
    COUNT(*)
FROM
    TRANSACTIONS
WHERE
    family_id = /* familyId */0
    AND deleted_at IS NULL
    AND (is_private = FALSE OR user_id = /* viewerUserId */0)
/*%if walletId != null */
    AND wallet_id = /* walletId */0
/*%end*/
/*%if categoryId != null */
    AND (category_id = /* categoryId */0
         OR category_id IN (SELECT c.id FROM CATEGORIES c WHERE c.parent_id = /* categoryId */0)
         OR id IN (SELECT s.transaction_id FROM TRANSACTION_SPLITS s
                   WHERE s.category_id = /* categoryId */0
                      OR s.category_id IN (SELECT c2.id FROM CATEGORIES c2 WHERE c2.parent_id = /* categoryId */0)))
/*%end*/
/*%if tagId != null */
    AND id IN (SELECT tt.transaction_id FROM TRANSACTION_TAGS tt WHERE tt.tag_id = /* tagId */0)
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
    AND id = /* targetId */0
