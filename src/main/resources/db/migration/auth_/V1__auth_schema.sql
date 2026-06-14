-- ===========================================================================
-- SAANJHA 2.0: AUTHENTICATION MODULE SCHEMA
-- Version: V1
-- Description: Core identity, sessions, refresh tokens, and OTP verifications
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS auth;

-- ---------------------------------------------------------------------------
-- 1. Identity: Auth Users
-- ---------------------------------------------------------------------------
CREATE TABLE auth.auth_users (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 email VARCHAR(255) NOT NULL,
                                 password_hash VARCHAR(255) NOT NULL,
                                 is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
                                 account_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    -- BaseAuditEntity Columns
                                 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                 created_by VARCHAR(255) DEFAULT 'SYSTEM',
                                 updated_by VARCHAR(255) DEFAULT 'SYSTEM'
);

-- Prevents race conditions on concurrent signups
CREATE UNIQUE INDEX idx_auth_users_email ON auth.auth_users (email);


-- ---------------------------------------------------------------------------
-- 2. Sessions: Device Connections
-- ---------------------------------------------------------------------------
CREATE TABLE auth.auth_sessions (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    user_id UUID NOT NULL REFERENCES auth.auth_users(id) ON DELETE CASCADE,
                                    device_id VARCHAR(255) NOT NULL,
                                    device_ip VARCHAR(45) NOT NULL,
                                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                                    last_activity_at TIMESTAMPTZ NOT NULL,

    -- BaseAuditEntity Columns
                                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                    created_by VARCHAR(255) DEFAULT 'SYSTEM',
                                    updated_by VARCHAR(255) DEFAULT 'SYSTEM'
);

-- Optimizes "Logout All Devices" and session validation queries
CREATE INDEX idx_auth_sessions_user_active ON auth.auth_sessions(user_id) WHERE is_active = true;


-- ---------------------------------------------------------------------------
-- 3. Cryptography: Refresh Tokens
-- ---------------------------------------------------------------------------
CREATE TABLE auth.auth_refresh_tokens (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          token_hash VARCHAR(255) NOT NULL,
                                          session_id UUID NOT NULL REFERENCES auth.auth_sessions(id) ON DELETE CASCADE,
                                          parent_token_id UUID,
                                          is_used BOOLEAN NOT NULL DEFAULT FALSE,
                                          is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
                                          expires_at TIMESTAMPTZ NOT NULL,

    -- BaseAuditEntity Columns
                                          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                          created_by VARCHAR(255) DEFAULT 'SYSTEM',
                                          updated_by VARCHAR(255) DEFAULT 'SYSTEM'
);

-- Fast lookup for rotation, unique constraint prevents hash collisions
CREATE UNIQUE INDEX idx_refresh_tokens_hash ON auth.auth_refresh_tokens(token_hash);
-- Fast lookup for revoking an entire session family
CREATE INDEX idx_refresh_tokens_session ON auth.auth_refresh_tokens(session_id);


-- ---------------------------------------------------------------------------
-- 4. Verification: OTPs and Magic Links
-- ---------------------------------------------------------------------------
CREATE TABLE auth.auth_verification_codes (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                              user_id UUID NOT NULL REFERENCES auth.auth_users(id) ON DELETE CASCADE,
                                              code_hash VARCHAR(255) NOT NULL,
                                              purpose VARCHAR(50) NOT NULL,
                                              expires_at TIMESTAMPTZ NOT NULL,
                                              is_used BOOLEAN NOT NULL DEFAULT FALSE,

    -- BaseAuditEntity Columns
                                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                              updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                              created_by VARCHAR(255) DEFAULT 'SYSTEM',
                                              updated_by VARCHAR(255) DEFAULT 'SYSTEM'
);

-- Partial index: Forces the database to only scan OTPs that are actually valid and unused,
-- ignoring millions of historical rows when verifying a code.
CREATE INDEX idx_verification_codes_lookup
    ON auth.auth_verification_codes (user_id, purpose)
    WHERE is_used = false;