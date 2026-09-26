-- Set only when /refresh rotates a token (not on logout/admin revoke), so replaying a ROTATED token can be
-- told apart from a legitimately revoked one — and a replay within a short grace window (two browser tabs
-- sharing one refresh token racing to refresh) from a real theft.
ALTER TABLE REFRESH_TOKENS
    ADD COLUMN rotated_at DATETIME NULL AFTER last_used_at;
