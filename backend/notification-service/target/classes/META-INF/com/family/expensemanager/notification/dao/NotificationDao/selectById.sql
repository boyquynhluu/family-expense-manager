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
    id = /* id */0
