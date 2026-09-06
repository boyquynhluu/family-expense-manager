SELECT
    id,
    family_id,
    name,
    type,
    icon,
    color
FROM
    CATEGORIES
WHERE
    family_id = /* familyId */0
    AND type = /* type */'EXPENSE'
ORDER BY
    id
