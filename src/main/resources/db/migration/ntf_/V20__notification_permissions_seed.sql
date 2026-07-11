-- ===========================================================================
-- SAANJHA 2.0: V20 MIGRATION (NOTIFICATION MODULE)
-- Seeds PBAC permissions.
-- ===========================================================================

-- 'notification:view'   -> any standard user: read their own notification
--                          feed and their own preferences.
-- 'notification:manage' -> any standard user: update their own preferences
--                          (channels, quiet hours, DND, digest mode).
-- 'notification:admin'  -> operational visibility/action on delivery
--                          failures and provider health - ROLE_ADMIN only.
--                          Deliberately separate from 'notification:manage':
--                          managing your OWN preferences and operating the
--                          delivery pipeline for every user are very
--                          different blast radii (same reasoning this
--                          codebase already applied splitting Team's
--                          'team:participate' from admin-only actions).
INSERT INTO auth.auth_permissions (name, description) VALUES
    ('notification:view',   'Can view own notification feed and own preferences'),
    ('notification:manage', 'Can update own notification preferences'),
    ('notification:admin',  'Can view provider health and manage the dead-letter queue for all users');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('notification:view', 'notification:manage');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('notification:view', 'notification:manage', 'notification:admin');
