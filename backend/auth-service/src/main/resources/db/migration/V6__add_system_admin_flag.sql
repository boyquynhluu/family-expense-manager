-- A system-wide super-admin flag, independent of the per-family OWNER/MEMBER role
-- (see USERS.role). There is no self-service way to become an admin; the first admin
-- must be flipped directly in the database, e.g.:
--   UPDATE USERS SET is_system_admin = TRUE WHERE email = 'you@example.com';
-- Existing admins can then promote/demote other users via the admin panel.
ALTER TABLE USERS
    ADD COLUMN is_system_admin BOOLEAN NOT NULL DEFAULT FALSE;
