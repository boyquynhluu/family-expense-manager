SELECT
    family_id,
    approval_threshold,
    updated_at
FROM
    FAMILY_SETTINGS
WHERE
    family_id = /* familyId */0
