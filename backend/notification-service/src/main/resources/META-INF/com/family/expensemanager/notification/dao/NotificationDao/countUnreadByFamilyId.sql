SELECT
    COUNT(*)
FROM
    NOTIFICATIONS
WHERE
    family_id = /* familyId */0
    AND is_read = FALSE
/*%if excludedTypes.size() > 0 */
    AND type NOT IN /* excludedTypes */('BUDGET_WARNING')
/*%end*/
