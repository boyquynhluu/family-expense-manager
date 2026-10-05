SELECT
    COUNT(*)
FROM
    RECURRING_DRAFTS
WHERE
    family_id = /* familyId */0
    AND status = 'PENDING'
