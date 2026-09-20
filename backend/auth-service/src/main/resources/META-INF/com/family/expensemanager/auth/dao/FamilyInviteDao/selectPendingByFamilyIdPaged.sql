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
    family_id = /* familyId */0
    AND accepted_at IS NULL
    AND expires_at > NOW()
ORDER BY
    created_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
