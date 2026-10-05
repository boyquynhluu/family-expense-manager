SELECT
    COALESCE(SUM(amount), 0)
FROM
    TRANSACTION_CATEGORY_LINES
WHERE
    family_id = /* familyId */0
    AND type = 'EXPENSE'
    AND deleted_at IS NULL
    AND occurred_at >= /* fromDate */'2026-01-01'
    AND occurred_at < /* toExclusive */'2026-02-01'
/*%if !allCategories */
    AND category_id IN /* categoryIds */(1)
/*%end*/
/*%if walletId != null */
    AND wallet_id = /* walletId */0
/*%end*/
/*%if userId != null */
    AND user_id = /* userId */0
/*%end*/
