-- Optimistic locking: two people in the same family editing the same budget or transaction at once
-- could otherwise silently overwrite each other's change. Doma maps a @Version field to this column
-- automatically: every UPDATE checks version = ? and increments it, so a stale write hits 0 affected
-- rows instead of overwriting — see GlobalExceptionHandler for how that's turned into a 409.
ALTER TABLE BUDGETS ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE TRANSACTIONS ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0;
