UPDATE
    TRANSFER_REQUESTS
SET
    status = /* status */'COMPLETED',
    decided_by_user_id = /* decidedByUserId */0,
    decided_by_name = /* decidedByName */'name',
    decided_at = /* decidedAt */'2026-01-01 00:00:00',
    transfer_id = /* transferId */0
WHERE
    id = /* id */0
    AND status = 'PENDING'
