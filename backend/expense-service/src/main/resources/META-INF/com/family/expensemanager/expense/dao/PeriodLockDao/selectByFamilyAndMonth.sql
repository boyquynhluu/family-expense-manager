SELECT
    family_id,
    period_month,
    locked_by_user_id,
    locked_by_name,
    locked_at
FROM
    PERIOD_LOCKS
WHERE
    family_id = /* familyId */0
    AND period_month = /* periodMonth */'2026-01'
