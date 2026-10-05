SELECT
    family_id,
    period_month,
    sent_at
FROM
    MONTHLY_SUMMARY_RUNS
WHERE
    family_id = /* familyId */0
    AND period_month = /* periodMonth */'2026-01'
