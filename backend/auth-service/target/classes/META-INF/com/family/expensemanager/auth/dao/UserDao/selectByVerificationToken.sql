SELECT
    id,
    family_id,
    email,
    password_hash,
    display_name,
    role,
    active,
    verification_token,
    verification_token_expires_at,
    provider,
    provider_id
FROM
    USERS
WHERE
    verification_token = /* verificationToken */'abc123'
