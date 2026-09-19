SELECT
    id,
    user_id,
    code_hash,
    used_at,
    created_at
FROM
    TWO_FACTOR_RECOVERY_CODES
WHERE
    user_id = /* userId */0
    AND used_at IS NULL
ORDER BY
    id
