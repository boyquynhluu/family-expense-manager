-- Free-text-ish family relationship label (Bố, Mẹ, Anh, Chị, Em, ...), purely
-- informational — no business logic depends on it, unlike the OWNER/MEMBER role.
ALTER TABLE USERS
    ADD COLUMN relationship VARCHAR(50) NULL;
