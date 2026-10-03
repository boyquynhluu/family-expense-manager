SELECT
    COUNT(*)
FROM
    TRANSFER_REQUESTS
WHERE
    family_id = /* familyId */0
    AND requester_user_id = /* requesterUserId */0
    AND status = 'PENDING'
