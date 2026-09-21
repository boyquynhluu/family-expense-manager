SELECT
    id,
    name,
    created_at
FROM
    FAMILIES
WHERE
    1 = 1
/*%if pattern != null */
    AND LOWER(name) LIKE /* pattern */'%a%' ESCAPE '!'
/*%end*/
ORDER BY
    id
LIMIT /* limit */20 OFFSET /* offset */0
