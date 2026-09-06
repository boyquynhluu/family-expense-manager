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
ORDER BY
    id
