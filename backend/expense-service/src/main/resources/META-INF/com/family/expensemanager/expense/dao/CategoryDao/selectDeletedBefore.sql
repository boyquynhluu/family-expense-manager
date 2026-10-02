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
    deleted_at IS NOT NULL
    AND deleted_at < /* cutoff */'2026-01-01 00:00:00'
ORDER BY
    id
