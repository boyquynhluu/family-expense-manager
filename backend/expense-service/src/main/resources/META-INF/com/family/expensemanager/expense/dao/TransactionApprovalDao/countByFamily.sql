SELECT
    COUNT(*)
FROM
    TRANSACTION_APPROVALS
WHERE
    family_id = /* familyId */0
/*%if requesterUserId != null */
    AND requester_user_id = /* requesterUserId */0
/*%end*/
