SELECT
    id,
    family_id,
    requester_user_id,
    requester_name,
    approver_user_id,
    from_wallet_id,
    to_wallet_id,
    amount,
    note,
    status,
    decided_by_user_id,
    decided_by_name,
    decided_at,
    transfer_id,
    created_at
FROM
    TRANSFER_REQUESTS
WHERE
    family_id = /* familyId */0
    AND (requester_user_id = /* userId */0 OR approver_user_id = /* userId */0)
ORDER BY
    status = 'PENDING' DESC, created_at DESC, id DESC
LIMIT /* limit */20 OFFSET /* offset */0
