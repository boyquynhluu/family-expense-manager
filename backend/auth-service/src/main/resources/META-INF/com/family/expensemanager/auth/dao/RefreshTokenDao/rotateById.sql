UPDATE
    REFRESH_TOKENS
SET
    revoked = true,
    rotated_at = /* rotatedAt */'2025-01-01 00:00:00'
WHERE
    id = /* id */0
    AND user_id = /* userId */0
    AND revoked = false
