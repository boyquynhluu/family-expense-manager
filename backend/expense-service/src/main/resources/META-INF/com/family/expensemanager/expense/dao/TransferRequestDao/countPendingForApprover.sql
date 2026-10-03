SELECT
    COUNT(*)
FROM
    TRANSFER_REQUESTS
WHERE
    family_id = /* familyId */0
    AND approver_user_id = /* approverUserId */0
    AND status = 'PENDING'
