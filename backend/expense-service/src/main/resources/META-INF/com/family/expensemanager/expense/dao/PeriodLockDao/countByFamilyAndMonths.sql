SELECT
    COUNT(*)
FROM
    PERIOD_LOCKS
WHERE
    family_id = /* familyId */0
    AND period_month IN /* periodMonths */('2026-01')
