SELECT
    family_id,
    user_id,
    daily_limit,
    monthly_limit,
    updated_at
FROM
    MEMBER_SPENDING_LIMITS
WHERE
    family_id = /* familyId */0
ORDER BY
    user_id
