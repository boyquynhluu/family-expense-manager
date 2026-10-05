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
    id = /* id */0
