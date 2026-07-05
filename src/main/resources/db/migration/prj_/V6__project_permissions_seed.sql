-- ===========================================================================
-- SAANJHA 2.0: V6 MIGRATION (PROJECT MODULE PBAC SEED)
-- Registers project-scoped permissions against the existing auth.auth_permissions
-- registry (owned by the Auth module) and wires them into the two system roles.
--
-- 'project:create'   -> any standard user may initialize a project.
-- 'project:manage'   -> any standard user may manage a project they own
--                       (ownership itself is enforced in code via ProjectSecurityGuard).
-- 'project:moderate' -> Admin-only override that bypasses ownership checks
--                       entirely (used for moderation / support interventions).
-- ===========================================================================

INSERT INTO auth.auth_permissions (name, description) VALUES
    ('project:create',   'Can initialize a new project'),
    ('project:manage',   'Can manage the scope, requirements, and lifecycle of an owned project'),
    ('project:moderate', 'Can manage or transition any project regardless of ownership');

-- Standard users can create and manage their own projects.
INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('project:create', 'project:manage');

-- Admins additionally get the moderation override.
INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('project:create', 'project:manage', 'project:moderate');
