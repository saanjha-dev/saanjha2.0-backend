-- ===========================================================================
-- SAANJHA 2.0: V27 MIGRATION (ADMIN / PLATFORM GOVERNANCE PBAC SEED)
--
-- 'report:submit'   -> any standard user: file a report against a user,
--                       project, team, chat message, or portfolio. The one
--                       Admin-module permission ROLE_USER holds — everything
--                       else in this module is Admin-only by design, since
--                       "governs the platform" is definitionally not a
--                       standard-user capability.
-- 'admin:moderate'  -> User/Project/Team/Chat moderation, Reports, Appeals,
--                       Trust & Safety, investigation notes, dashboard reads.
-- 'admin:configure' -> Feature flags, platform settings, maintenance mode.
-- 'admin:announce'  -> Platform announcements (kept separate from
--                       admin:configure so a future "Communications" admin
--                       sub-role could manage announcements without also
--                       holding platform-configuration power).
-- 'admin:audit'      -> Read-only access to the audit ledger and moderation
--                       timeline — kept separate from admin:moderate so a
--                       future Compliance/read-only reviewer role can be
--                       introduced without an API change (see
--                       AdminAuditController's javadoc).
-- ===========================================================================

INSERT INTO auth.auth_permissions (name, description) VALUES
    ('report:submit',   'Can file a report against a user, project, team, chat message, or portfolio'),
    ('admin:moderate',  'Can take moderation actions on users, projects, teams, chat, reports, and appeals'),
    ('admin:configure', 'Can manage feature flags and platform configuration, including maintenance mode'),
    ('admin:announce',  'Can create and manage platform announcements'),
    ('admin:audit',     'Can read the audit ledger and moderation timeline');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('report:submit');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('report:submit', 'admin:moderate', 'admin:configure', 'admin:announce', 'admin:audit');
