SELECT
    COUNT(*)
FROM
    USERS
WHERE
    1 = 1
/*%if pattern != null */
    AND (LOWER(email) LIKE /* pattern */'%a%' ESCAPE '!'
         OR LOWER(display_name) LIKE /* pattern */'%a%' ESCAPE '!')
/*%end*/
