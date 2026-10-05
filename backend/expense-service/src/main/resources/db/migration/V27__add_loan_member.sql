-- B3: which family member the loan belongs to ("thành viên đứng tên") — the one who borrowed or lent — as
-- opposed to counterparty_name (the person OUTSIDE the family) and created_by_user_id (who typed it in).
-- Existing loans: whoever recorded them.
ALTER TABLE LOANS
    ADD COLUMN member_user_id BIGINT UNSIGNED NULL AFTER family_id;

UPDATE LOANS SET member_user_id = created_by_user_id WHERE member_user_id IS NULL;

ALTER TABLE LOANS MODIFY COLUMN member_user_id BIGINT UNSIGNED NOT NULL;

CREATE INDEX idx_loans_family_member ON LOANS (family_id, member_user_id);
