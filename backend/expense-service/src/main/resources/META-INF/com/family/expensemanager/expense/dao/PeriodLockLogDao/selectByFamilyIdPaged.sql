SELECT
    id,
    family_id,
    period_month,
    action,
    actor_user_id,
    actor_name,
    created_at
FROM
    PERIOD_LOCK_LOGS
WHERE
    family_id = /* familyId */0
ORDER BY
    created_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
