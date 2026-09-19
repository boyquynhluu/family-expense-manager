UPDATE
    WALLETS
SET
    deleted_at = NULL
WHERE
    id = /* id */0
    AND family_id = /* familyId */0
    AND deleted_at IS NOT NULL
