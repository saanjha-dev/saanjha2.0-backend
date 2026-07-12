-- ===========================================================================
-- SAANJHA 2.0: V23 MIGRATION (DISCOVERY MODULE)
-- Seeds PBAC permissions. Search/trending/technology-browse itself is
-- permitAll() at the filter-chain level (SecurityConfig) — the Builder/Lead/
-- Recruiter personas (MES §0.3) all need to browse anonymously, same
-- reasoning as Project's public listing and Portfolio's public routes.
-- These two permissions gate only the personalized, per-user surface:
-- saved searches, search history, and the personalized feed/recommendations.
-- ===========================================================================

INSERT INTO auth.auth_permissions (name, description) VALUES
    ('discovery:view',   'Can view own search history, saved searches, and personalized recommendations'),
    ('discovery:manage', 'Can create/update/delete own saved searches and clear own search history');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('discovery:view', 'discovery:manage');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('discovery:view', 'discovery:manage');
