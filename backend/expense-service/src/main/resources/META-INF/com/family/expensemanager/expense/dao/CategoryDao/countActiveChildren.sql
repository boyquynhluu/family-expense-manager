SELECT
    COUNT(*)
FROM
    CATEGORIES
WHERE
    parent_id = /* parentId */0
    AND deleted_at IS NULL
