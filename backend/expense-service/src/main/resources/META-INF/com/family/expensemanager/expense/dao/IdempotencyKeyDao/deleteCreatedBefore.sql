DELETE FROM
    IDEMPOTENCY_KEYS
WHERE
    created_at < /* cutoff */'2026-01-01 00:00:00'
