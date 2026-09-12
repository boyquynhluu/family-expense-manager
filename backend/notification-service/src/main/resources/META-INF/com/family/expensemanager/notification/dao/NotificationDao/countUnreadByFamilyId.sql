SELECT
    COUNT(*)
FROM
    NOTIFICATIONS
WHERE
    family_id = /* familyId */0
    AND is_read = FALSE
