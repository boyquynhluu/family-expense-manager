SELECT
    COUNT(*)
FROM
    FAMILIES
WHERE
    1 = 1
/*%if pattern != null */
    AND LOWER(name) LIKE /* pattern */'%a%' ESCAPE '!'
/*%end*/
