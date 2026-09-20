-- NULL means the rule has never generated a transaction yet (frontend shows "Chưa thực
-- hiện"); once processDueRule fires it for the first time this is set to that
-- occurrence's date (frontend shows "Hoàn thành").
ALTER TABLE RECURRING_TRANSACTIONS ADD COLUMN last_run_date DATE NULL AFTER next_run_date;
