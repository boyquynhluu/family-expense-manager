SELECT
    id,
    user_id,
    token_hash,
    expires_at,
    revoked,
    created_at,
    device_info,
    ip_address,
    last_used_at,
    rotated_at
FROM
    REFRESH_TOKENS
WHERE
    token_hash = /* tokenHash */'x'
