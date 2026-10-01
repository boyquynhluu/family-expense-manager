-- A wallet can now belong to one family member ("ví riêng") or to nobody ("ví chung", usable by the
-- whole family). Before this, every wallet was implicitly shared, so any member could record money
-- into or out of e.g. "Ví Vợ" — WalletService.requireUsableBy now enforces the ownership.
-- Existing wallets stay shared (NULL) so nobody loses access on upgrade; the family OWNER assigns
-- owners afterwards from the Wallets page.
ALTER TABLE WALLETS
    ADD COLUMN owner_user_id BIGINT UNSIGNED NULL AFTER family_id;
