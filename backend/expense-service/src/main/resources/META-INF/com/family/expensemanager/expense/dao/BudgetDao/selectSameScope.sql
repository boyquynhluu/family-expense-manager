SELECT
    id,
    family_id,
    category_id,
    wallet_id,
    user_id,
    period_type,
    period_month,
    limit_amount,
    rollover,
    version
FROM
    BUDGETS
WHERE
    family_id = /* familyId */0
    AND period_type = /* periodType */'MONTH'
    AND period_month = /* periodMonth */'2026-01'
/*%if categoryId == null */
    AND category_id IS NULL
/*%else*/
    AND category_id = /* categoryId */0
/*%end*/
/*%if walletId == null */
    AND wallet_id IS NULL
/*%else*/
    AND wallet_id = /* walletId */0
/*%end*/
/*%if userId == null */
    AND user_id IS NULL
/*%else*/
    AND user_id = /* userId */0
/*%end*/
