SELECT
    id,
    family_id,
    email,
    password_hash,
    display_name,
    role,
    active,
    provider,
    provider_id
FROM
    USERS
WHERE
    email = /* email */'test@gmail.com'
