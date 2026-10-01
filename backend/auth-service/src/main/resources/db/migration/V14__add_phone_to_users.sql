-- Optional phone number that can be used instead of the email to log in (still with the password).
-- Stored normalised to +84XXXXXXXXX (see PhoneNumbers), so "0912 345 678" and "+84912345678" are the same
-- number; the UNIQUE key keeps one account per number (MySQL allows any number of NULLs under it).

ALTER TABLE USERS
    ADD COLUMN phone VARCHAR(20) NULL AFTER email,
    ADD CONSTRAINT uk_users_phone UNIQUE (phone);
