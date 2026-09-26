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
    user_id = /* userId */0
    AND revoked = false
    AND expires_at > NOW()
ORDER BY
    last_used_at DESC, created_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
