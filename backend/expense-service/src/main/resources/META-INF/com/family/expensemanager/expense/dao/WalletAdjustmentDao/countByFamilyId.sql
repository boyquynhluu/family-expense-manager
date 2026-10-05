SELECT
    COUNT(*)
FROM
    WALLET_ADJUSTMENTS
WHERE
    family_id = /* familyId */0
/*%if walletId != null */
    AND wallet_id = /* walletId */0
/*%end*/
