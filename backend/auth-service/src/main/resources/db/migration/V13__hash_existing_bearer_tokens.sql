-- AuthService now stores only SHA-256(token) (lowercase hex, same as MySQL's SHA2(x, 256)) for every
-- emailed bearer token and looks rows up by that hash. Tokens issued before that change are still raw
-- in these columns, so every verification / reset / e-mail change / invite link already sitting in someone's
-- inbox would stop working at deploy — hash them in place instead.
-- Raw tokens are 43-char base64url (46 with the "ec." e-mail-change prefix), never 64-char hex, so the
-- REGEXP guard skips values that are already hashed (e.g. a dev DB that ran the new code first).

UPDATE USERS
SET verification_token = SHA2(verification_token, 256)
WHERE verification_token IS NOT NULL
  AND verification_token NOT REGEXP '^[0-9a-f]{64}$';

UPDATE USERS
SET reset_password_token = SHA2(reset_password_token, 256)
WHERE reset_password_token IS NOT NULL
  AND reset_password_token NOT REGEXP '^[0-9a-f]{64}$';

UPDATE USERS
SET pending_email_token = SHA2(pending_email_token, 256)
WHERE pending_email_token IS NOT NULL
  AND pending_email_token NOT REGEXP '^[0-9a-f]{64}$';

UPDATE FAMILY_INVITES
SET token = SHA2(token, 256)
WHERE token IS NOT NULL
  AND token NOT REGEXP '^[0-9a-f]{64}$';
