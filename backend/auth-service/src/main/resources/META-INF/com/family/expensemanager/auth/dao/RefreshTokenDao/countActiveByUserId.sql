SELECT
    COUNT(*)
FROM
    REFRESH_TOKENS
WHERE
    user_id = /* userId */0
    AND revoked = false
    AND expires_at > NOW()
