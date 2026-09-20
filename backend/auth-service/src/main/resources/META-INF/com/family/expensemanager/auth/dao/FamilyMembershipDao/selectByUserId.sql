SELECT
    id,
    user_id,
    family_id,
    role,
    created_at
FROM
    FAMILY_MEMBERSHIPS
WHERE
    user_id = /* userId */0
ORDER BY
    id
