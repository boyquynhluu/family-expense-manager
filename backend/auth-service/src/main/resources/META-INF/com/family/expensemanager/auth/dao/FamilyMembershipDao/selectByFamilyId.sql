SELECT
    id,
    user_id,
    family_id,
    role,
    created_at
FROM
    FAMILY_MEMBERSHIPS
WHERE
    family_id = /* familyId */0
ORDER BY
    id
