-- ===========================================================================
-- SAANJHA 2.0: V11 MIGRATION (TEAM MODULE PBAC SEED)
--
-- 'team:participate' -> any standard user: view a team they belong to (subject
--                        to visibility settings), view their own membership,
--                        leave a team. Self-service actions only.
-- 'team:manage'       -> any standard user: Lead-only actions (remove a
--                        member, transfer leadership, update settings, lock/
--                        unlock) for a team they lead. Ownership enforced in
--                        code via TeamSecurityGuard — not duplicated here.
-- 'team:moderate'      -> Admin-only override, including dissolve, which no
--                        Lead can do to their own team.
-- ===========================================================================

INSERT INTO auth.auth_permissions (name, description) VALUES
    ('team:participate', 'Can view teams one belongs to, view own membership, and leave a team'),
    ('team:manage',      'Can manage roster, leadership, and settings for a team one leads'),
    ('team:moderate',    'Can manage or dissolve any team regardless of leadership');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('team:participate', 'team:manage');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('team:participate', 'team:manage', 'team:moderate');
