SELECT
    email
FROM
    USERS
WHERE
    is_system_admin = TRUE
    AND active = TRUE
    AND locked = FALSE
ORDER BY
    id
