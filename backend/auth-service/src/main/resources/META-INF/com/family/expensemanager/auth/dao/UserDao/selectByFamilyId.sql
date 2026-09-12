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
    verification_token,
    verification_token_expires_at,
    reset_password_token,
    reset_password_token_expires_at,
    is_system_admin,
    relationship
FROM
    USERS
WHERE
    family_id = /* familyId */0
ORDER BY
    id
