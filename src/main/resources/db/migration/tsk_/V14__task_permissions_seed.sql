-- ===========================================================================
-- SAANJHA 2.0: V14 MIGRATION (TASK MODULE PBAC SEED)
--
-- 'task:create' -> any standard user may create tasks for a project whose
--                  team they belong to (membership enforced in code).
-- 'task:manage' -> any standard user may edit/move/assign tasks within a
--                  project whose team they belong to.
-- 'task:moderate' -> Admin-only override.
-- ===========================================================================

INSERT INTO auth.auth_permissions (name, description) VALUES
    ('task:create',   'Can create tasks for a project one is a team member of'),
    ('task:manage',   'Can edit, move, assign, and manage tasks for a project one is a team member of'),
    ('task:moderate', 'Can manage any task regardless of team membership');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('task:create', 'task:manage');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('task:create', 'task:manage', 'task:moderate');
