SELECT
    id,
    family_id,
    transaction_id,
    action,
    actor_user_id,
    actor_name,
    before_json,
    after_json,
    created_at
FROM
    TRANSACTION_AUDIT_LOGS
WHERE
    transaction_id = /* transactionId */0
    AND family_id = /* familyId */0
ORDER BY
    id
