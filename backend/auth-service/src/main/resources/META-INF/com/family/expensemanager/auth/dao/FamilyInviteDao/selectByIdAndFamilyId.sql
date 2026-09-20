SELECT
    id,
    family_id,
    email,
    token,
    invited_by_user_id,
    expires_at,
    accepted_at,
    created_at
FROM
    FAMILY_INVITES
WHERE
    id = /* id */0
    AND family_id = /* familyId */0
