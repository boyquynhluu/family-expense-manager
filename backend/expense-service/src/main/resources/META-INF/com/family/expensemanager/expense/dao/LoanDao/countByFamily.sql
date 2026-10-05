SELECT
    COUNT(*)
FROM
    LOANS
WHERE
    family_id = /* familyId */0
/*%if memberUserId != null */
    AND member_user_id = /* memberUserId */0
/*%end*/
/*%if status != null */
    AND status = /* status */'OPEN'
/*%end*/
