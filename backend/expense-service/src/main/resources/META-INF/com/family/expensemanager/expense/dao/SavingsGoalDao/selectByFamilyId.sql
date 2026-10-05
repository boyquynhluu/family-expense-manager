SELECT
    id,
    family_id,
    name,
    target_amount,
    deadline,
    wallet_id,
    milestone_reached,
    archived,
    created_by_user_id,
    created_by_name,
    created_at
FROM
    SAVINGS_GOALS
WHERE
    family_id = /* familyId */0
ORDER BY
    archived, deadline IS NULL, deadline, id
