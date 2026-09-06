SELECT
    id,
    family_id,
    email,
    password_hash,
    display_name,
    role,
    active
FROM
    USERS
WHERE
    email = /* email */'test@gmail.com'
