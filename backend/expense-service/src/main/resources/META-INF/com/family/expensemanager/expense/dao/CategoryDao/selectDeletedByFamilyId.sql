SELECT
    id,
    family_id,
    name,
    type,
    icon,
    color,
    deleted_at
FROM
    CATEGORIES
WHERE
    family_id = /* familyId */0
    AND deleted_at IS NOT NULL
ORDER BY
    deleted_at DESC
