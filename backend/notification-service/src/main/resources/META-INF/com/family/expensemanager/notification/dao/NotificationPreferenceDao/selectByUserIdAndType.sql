SELECT
    user_id,
    type,
    in_app_enabled,
    email_enabled
FROM
    NOTIFICATION_PREFERENCES
WHERE
    user_id = /* userId */0
    AND type = /* type */'BUDGET_EXCEEDED'
