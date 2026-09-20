SELECT
    COUNT(*)
FROM
    FAMILY_INVITES
WHERE
    family_id = /* familyId */0
    AND accepted_at IS NULL
    AND expires_at > NOW()
