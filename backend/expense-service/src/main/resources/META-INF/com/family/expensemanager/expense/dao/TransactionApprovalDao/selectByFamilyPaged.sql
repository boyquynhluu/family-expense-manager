SELECT
    id,
    family_id,
    requester_user_id,
    requester_name,
    requester_email,
    wallet_id,
    category_id,
    amount,
    occurred_at,
    note,
    is_private,
    status,
    decided_by_user_id,
    decided_by_name,
    decided_at,
    reject_reason,
    transaction_id,
    created_at
FROM
    TRANSACTION_APPROVALS
WHERE
    family_id = /* familyId */0
/*%if requesterUserId != null */
    AND requester_user_id = /* requesterUserId */0
/*%end*/
ORDER BY
    CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, created_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
