-- ===========================================================================
-- SAANJHA 2.0: V9 MIGRATION (APPLICATION MODULE PBAC SEED)
--
-- 'application:submit'   -> any standard user may apply, withdraw, and respond
--                           to invitations addressed to them (both are "the
--                           applicant/invitee acting on their own behalf").
-- 'application:manage'   -> any standard user may review applications and
--                           send/revoke invitations for projects they lead
--                           (ownership enforced in code, delegating to
--                           ProjectSecurityGuard.isLead — Application does not
--                           duplicate that logic).
-- 'application:moderate' -> Admin-only override that bypasses ownership.
-- ===========================================================================

INSERT INTO auth.auth_permissions (name, description) VALUES
    ('application:submit',   'Can apply to projects, withdraw own applications, and respond to invitations'),
    ('application:manage',   'Can review applications and manage invitations for an owned project'),
    ('application:moderate', 'Can manage any project''s applications and invitations regardless of ownership');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('application:submit', 'application:manage');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('application:submit', 'application:manage', 'application:moderate');
