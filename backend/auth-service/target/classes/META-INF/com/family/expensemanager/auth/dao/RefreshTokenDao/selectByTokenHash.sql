SELECT
    id,
    user_id,
    token_hash,
    expires_at,
    revoked
FROM
    REFRESH_TOKENS
WHERE
    token_hash = /* tokenHash */'x'
