UPDATE
    TRANSACTIONS
SET
    deleted_at = NULL,
    deleted_by_user_id = NULL,
    deleted_by_name = NULL
WHERE
    id = /* id */0
    AND family_id = /* familyId */0
    AND deleted_at IS NOT NULL
