SELECT
    id,
    family_id,
    entity_type,
    entity_id,
    action,
    actor_user_id,
    actor_name,
    before_json,
    after_json,
    created_at
FROM
    ENTITY_AUDIT_LOGS
WHERE
    family_id = /* familyId */0
    AND entity_type = /* entityType */'WALLET'
    AND entity_id = /* entityId */0
ORDER BY
    id
