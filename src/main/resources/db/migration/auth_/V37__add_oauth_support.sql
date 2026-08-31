-- ===========================================================================
-- SAANJHA 2.0: ADD OAUTH2 SUPPORT TO AUTH_USERS
-- Version: V36
-- Description: Adds auth_provider, provider_id, makes password optional
-- ===========================================================================

-- 1. Add auth_provider to support LOCAL, GOOGLE, GITHUB
ALTER TABLE auth.auth_users
    ADD COLUMN auth_provider VARCHAR(50) DEFAULT 'LOCAL' NOT NULL;

-- 2. Add provider_id to store the unique id from the OAuth provider
ALTER TABLE auth.auth_users
    ADD COLUMN provider_id VARCHAR(255);

-- 3. Make password_hash nullable because OAuth users will not have a password
ALTER TABLE auth.auth_users
    ALTER COLUMN password_hash DROP NOT NULL;

-- 4. Create an index on provider_id for fast lookup during OAuth login
CREATE INDEX idx_auth_users_provider ON auth.auth_users (auth_provider, provider_id);
