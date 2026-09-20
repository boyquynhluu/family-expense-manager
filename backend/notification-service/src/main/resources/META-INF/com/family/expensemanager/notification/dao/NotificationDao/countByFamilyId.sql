SELECT
    COUNT(*)
FROM
    NOTIFICATIONS
WHERE
    family_id = /* familyId */0
/*%if excludedTypes.size() > 0 */
    AND type NOT IN /* excludedTypes */('BUDGET_WARNING')
/*%end*/
