-- Lets one account belong to multiple families (README "6. 1 tài khoản chỉ thuộc đúng
-- 1 gia đình"). USERS.family_id/role are kept as-is and now mean "the family/role this
-- user is CURRENTLY active in" — every existing query/JWT claim built from them stays
-- valid unchanged. This table is the actual source of truth for "which families does
-- this user belong to, with what role in each"; switching families (see AuthService
-- .switchFamily) just repoints USERS.family_id/role at a different membership row and
-- re-issues a token, so api-gateway/expense-service/notification-service — which only
-- ever see one familyId per request via the JWT — need no changes at all.
CREATE TABLE FAMILY_MEMBERSHIPS (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    family_id  BIGINT UNSIGNED NOT NULL,
    role       VARCHAR(30)     NOT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_family_memberships_user_family UNIQUE (user_id, family_id),
    CONSTRAINT fk_family_memberships_user FOREIGN KEY (user_id) REFERENCES USERS (id),
    CONSTRAINT fk_family_memberships_family FOREIGN KEY (family_id) REFERENCES FAMILIES (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_family_memberships_family_id ON FAMILY_MEMBERSHIPS (family_id);

-- Backfill: every existing user's current family_id/role becomes their first membership.
INSERT INTO FAMILY_MEMBERSHIPS (user_id, family_id, role, created_at)
SELECT id, family_id, role, NOW() FROM USERS;
