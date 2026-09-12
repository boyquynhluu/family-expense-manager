ALTER TABLE USERS
    MODIFY COLUMN password_hash VARCHAR(255) NULL,
    ADD COLUMN provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN provider_id VARCHAR(255) NULL;

-- MySQL treats each NULL as distinct in a unique index, so any number of LOCAL
-- users (provider_id always NULL) can coexist; only (provider, provider_id) pairs
-- for actual OAuth2 accounts are required to be unique.
CREATE UNIQUE INDEX uk_users_provider_provider_id ON USERS (provider, provider_id);
