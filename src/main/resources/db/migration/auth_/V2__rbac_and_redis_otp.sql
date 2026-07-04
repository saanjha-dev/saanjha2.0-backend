-- ===========================================================================
-- SAANJHA 2.0: V2 MIGRATION
-- 1. Move OTPs to Redis (Drop Postgres Table)
-- 2. Implement Permission-Based Access Control (PBAC)
-- ===========================================================================

-- 1. DROP THE POSTGRES OTP TABLE (Handled by Redis TTL now)
DROP TABLE IF EXISTS auth.auth_verification_codes;


-- 2. PBAC: CREATE BASE ENTITIES
CREATE TABLE auth.auth_roles (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 name VARCHAR(50) UNIQUE NOT NULL, -- e.g., 'ROLE_ADMIN', 'ROLE_USER'
                                 description VARCHAR(255)
);

CREATE TABLE auth.auth_permissions (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       name VARCHAR(100) UNIQUE NOT NULL, -- e.g., 'project:read', 'user:delete'
                                       description VARCHAR(255)
);


-- 3. PBAC: CREATE MAPPING TABLES (Many-to-Many)
CREATE TABLE auth.auth_role_permissions (
                                            role_id UUID REFERENCES auth.auth_roles(id) ON DELETE CASCADE,
                                            permission_id UUID REFERENCES auth.auth_permissions(id) ON DELETE CASCADE,
                                            PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE auth.auth_user_roles (
                                      user_id UUID REFERENCES auth.auth_users(id) ON DELETE CASCADE,
                                      role_id UUID REFERENCES auth.auth_roles(id) ON DELETE CASCADE,
                                      PRIMARY KEY (user_id, role_id)
);


-- 4. PBAC: SEED DEFAULT SYSTEM DATA
INSERT INTO auth.auth_roles (name, description) VALUES
                                                    ('ROLE_USER', 'Standard authenticated user'),
                                                    ('ROLE_ADMIN', 'System Administrator');

INSERT INTO auth.auth_permissions (name, description) VALUES
                                                          ('profile:read', 'Can read own profile'),
                                                          ('profile:write', 'Can update own profile'),
                                                          ('user:read', 'Can read all users'),
                                                          ('user:delete', 'Can delete users');

-- Give ROLE_USER basic permissions
INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('profile:read', 'profile:write');

-- Give ROLE_ADMIN all permissions
INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN';