SELECT
    id,
    family_id,
    scope,
    idempotency_key,
    request_hash,
    response_json,
    created_at
FROM
    IDEMPOTENCY_KEYS
WHERE
    family_id = /* familyId */0
    AND scope = /* scope */'CREATE_TRANSACTION'
    AND idempotency_key = /* idempotencyKey */'abc123'
