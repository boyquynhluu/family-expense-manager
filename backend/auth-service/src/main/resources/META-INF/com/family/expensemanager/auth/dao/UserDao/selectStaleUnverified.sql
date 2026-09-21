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
    relationship,
    totp_secret,
    totp_enabled,
    locked,
    locked_at,
    pending_email,
    pending_email_token,
    pending_email_expires_at,
    totp_last_step
FROM
    USERS
WHERE
    active = FALSE
    AND locked = FALSE
    AND provider = 'LOCAL'
    AND password_hash IS NOT NULL
    AND verification_token_expires_at IS NOT NULL
    AND verification_token_expires_at < /* cutoff */'2000-01-01 00:00:00'
ORDER BY
    id
