SELECT
    id,
    family_id,
    name,
    created_at
FROM
    TAGS
WHERE
    family_id = /* familyId */0
ORDER BY
    name
