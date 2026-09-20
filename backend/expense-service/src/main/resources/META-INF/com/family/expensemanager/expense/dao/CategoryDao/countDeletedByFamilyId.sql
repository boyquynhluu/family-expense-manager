SELECT
    COUNT(*)
FROM
    CATEGORIES
WHERE
    family_id = /* familyId */0
    AND deleted_at IS NOT NULL
