UPDATE
    REFRESH_TOKENS
SET
    revoked = true
WHERE
    user_id = /* userId */0
    AND id != /* exceptId */0
    AND revoked = false
