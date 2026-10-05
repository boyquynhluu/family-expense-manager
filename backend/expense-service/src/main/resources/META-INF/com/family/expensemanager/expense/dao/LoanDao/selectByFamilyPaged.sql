SELECT
    id,
    family_id,
    member_user_id,
    direction,
    counterparty_name,
    counterparty_contact,
    counterparty_wallet_id,
    principal,
    wallet_id,
    start_date,
    due_date,
    note,
    status,
    last_reminded_for,
    created_by_user_id,
    created_by_name,
    created_at
FROM
    LOANS
WHERE
    family_id = /* familyId */0
/*%if memberUserId != null */
    AND member_user_id = /* memberUserId */0
/*%end*/
/*%if status != null */
    AND status = /* status */'OPEN'
/*%end*/
ORDER BY
    CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END, due_date IS NULL, due_date, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
