SELECT
    COUNT(*)
FROM
    TRANSACTION_APPROVALS
WHERE
    family_id = /* familyId */0
    AND status = 'PENDING'
