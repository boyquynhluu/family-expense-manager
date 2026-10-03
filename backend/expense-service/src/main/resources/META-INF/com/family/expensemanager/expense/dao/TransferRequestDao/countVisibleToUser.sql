SELECT
    COUNT(*)
FROM
    TRANSFER_REQUESTS
WHERE
    family_id = /* familyId */0
    AND (requester_user_id = /* userId */0 OR approver_user_id = /* userId */0)
