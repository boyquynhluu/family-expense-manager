-- Login looks phones up by exact match on the normalised +84XXXXXXXXX form (PhoneNumbers.normalize), so a
-- number written any other way (e.g. "0964493848" typed straight into the DB) can never log in.
-- Normalise what is there now, drop what can't be fixed, and let MySQL refuse any other format from now on.

UPDATE USERS
SET phone = REGEXP_REPLACE(phone, '[ .()-]', '')
WHERE phone IS NOT NULL;

UPDATE USERS
SET phone = CONCAT('+84', SUBSTRING(phone, 2))
WHERE phone REGEXP '^0[35789][0-9]{8}$';

UPDATE USERS
SET phone = CONCAT('+', phone)
WHERE phone REGEXP '^84[35789][0-9]{8}$';

UPDATE USERS
SET phone = NULL
WHERE phone IS NOT NULL
  AND phone NOT REGEXP '^[+]84[35789][0-9]{8}$';

-- "[+]" rather than "\\+": the backslash form silently never matched on this MySQL.
ALTER TABLE USERS
    ADD CONSTRAINT chk_users_phone_format CHECK (phone IS NULL OR phone REGEXP '^[+]84[35789][0-9]{8}$');
