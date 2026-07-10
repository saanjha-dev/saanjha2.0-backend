-- ===========================================================================
-- SAANJHA 2.0: V18 MIGRATION (PORTFOLIO MODULE)
-- Seeds PBAC permissions. No 'portfolio:moderate' yet — this module has no
-- admin-facing moderation action today (no manual badge grant/revoke, no
-- entry correction). Add one when/if such an action is actually built,
-- rather than seeding a permission nothing checks yet.
-- ===========================================================================

-- 'portfolio:view'   -> any standard user: view their own portfolio/insights,
--                       and (subject to PortfolioService's own visibility
--                       gate) any user's public/shared portfolio.
-- 'portfolio:manage' -> any standard user: change their own visibility
--                       setting and issue/rotate their own share link.
INSERT INTO auth.auth_permissions (name, description) VALUES
    ('portfolio:view',   'Can view own portfolio, insights, and timeline, and any public/shared portfolio'),
    ('portfolio:manage', 'Can change own portfolio visibility and issue share links');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name IN ('portfolio:view', 'portfolio:manage');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('portfolio:view', 'portfolio:manage');
