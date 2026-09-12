SELECT
    id,
    family_id,
    email,
    password_hash,
    display_name,
    role,
    active,
    provider,
    provider_id,
    reset_password_token,
    reset_password_token_expires_at
FROM
    USERS
WHERE
    email = /* email */'test@gmail.com'
