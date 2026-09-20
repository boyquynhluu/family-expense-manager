-- Snapshot of the creator's display name at creation time. User names live in
-- auth-service (a separate database), so this lets the UI still show who created a
-- transaction after that member left the family. Existing rows stay NULL.
ALTER TABLE TRANSACTIONS
    ADD COLUMN created_by_name VARCHAR(100) NULL;
