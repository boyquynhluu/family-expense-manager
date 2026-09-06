SELECT
    id,
    family_id,
    user_id,
    type,
    title,
    message,
    payload_json,
    is_read
FROM
    NOTIFICATIONS
WHERE
    family_id = /* familyId */0
ORDER BY
    id DESC
