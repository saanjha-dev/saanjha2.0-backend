-- ===========================================================================
-- SAANJHA 2.0: V25 MIGRATION (CHAT MODULE PBAC SEED)
--
-- 'chat:participate' -> any standard user: send/edit/delete own messages,
--                        react, read, pin (if member permits), draft, in a
--                        conversation they belong to. Membership itself is
--                        enforced in code via ChatSecurityGuard, exactly the
--                        same split team:participate uses for team:manage -
--                        this permission is necessary but not sufficient.
-- 'chat:manage'       -> any standard user: OWNER/ADMIN-role actions for a
--                        conversation they administer (mute/remove a member,
--                        lock/unlock, update settings, delete others' messages).
-- 'chat:moderate'     -> Admin-only global override, including cross-
--                        conversation moderation and unblocking a user.
-- ===========================================================================

INSERT INTO auth.auth_permissions (name, description) VALUES
    ('chat:participate', 'Can send/read/react in conversations one belongs to'),
    ('chat:manage',      'Can administer a conversation one owns or admins (roster, lock, settings)'),
    ('chat:moderate',    'Can moderate or lock any conversation regardless of membership');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('chat:participate', 'chat:manage');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('chat:participate', 'chat:manage', 'chat:moderate');
