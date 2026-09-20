SELECT
    id,
    family_id,
    user_id,
    type,
    title,
    message,
    payload_json,
    is_read
FROM
    NOTIFICATIONS
WHERE
    family_id = /* familyId */0
/*%if excludedTypes.size() > 0 */
    AND type NOT IN /* excludedTypes */('BUDGET_WARNING')
/*%end*/
ORDER BY
    id DESC
LIMIT /* limit */20 OFFSET /* offset */0
