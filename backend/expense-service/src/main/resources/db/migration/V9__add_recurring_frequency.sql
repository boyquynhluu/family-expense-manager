-- MONTHLY keeps using day_of_month; WEEKLY uses day_of_week (1=Monday..7=Sunday);
-- YEARLY uses month_of_year + day_of_month. day_of_month stays NOT NULL (WEEKLY rows
-- store a placeholder 1) so the existing CHECK constraint and column are untouched.
ALTER TABLE RECURRING_TRANSACTIONS
    ADD COLUMN frequency VARCHAR(10) NOT NULL DEFAULT 'MONTHLY' AFTER day_of_month,
    ADD COLUMN day_of_week TINYINT NULL AFTER frequency,
    ADD COLUMN month_of_year TINYINT NULL AFTER day_of_week;
