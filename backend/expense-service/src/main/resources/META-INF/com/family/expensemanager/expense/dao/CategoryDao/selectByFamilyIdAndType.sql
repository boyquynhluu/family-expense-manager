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
    AND type = /* type */'EXPENSE'
    AND deleted_at IS NULL
ORDER BY
    id
