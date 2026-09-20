UPDATE
    REFRESH_TOKENS
SET
    revoked = true
WHERE
    id = /* id */0
    AND user_id = /* userId */0
    AND revoked = false
