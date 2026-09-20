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
    id = /* id */0
    AND deleted_at IS NULL
