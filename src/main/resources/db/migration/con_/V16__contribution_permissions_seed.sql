-- ===========================================================================
-- SAANJHA 2.0: V16 MIGRATION (CONTRIBUTION MODULE)
-- Seeds version 1 of the scoring weights (configurable defaults, mirroring
-- the base weights implied by MES Section E's C_base formula, extended to
-- the fuller contribution taxonomy this module supports) and PBAC permissions.
-- ===========================================================================

INSERT INTO con.con_scoring_weights (version, contribution_type, base_weight, created_by) VALUES
    (1, 'TASK_COMPLETION', 10.0, 'SYSTEM'),
    (1, 'TASK_REVIEW',       6.0, 'SYSTEM'),
    (1, 'LEADERSHIP',        15.0, 'SYSTEM'),
    (1, 'MENTORSHIP',        8.0, 'SYSTEM'),
    (1, 'PLANNING',          4.0, 'SYSTEM'),
    (1, 'TASK_ABANDONED',    0.0, 'SYSTEM'); -- Tracked for reliability reputation, never scored as positive contribution.

-- 'contribution:view'     -> any standard user: view their own contribution
--                            data, and any PUBLIC-visibility profile's.
-- 'contribution:moderate' -> Admin-only: issue corrections, edit scoring
--                            weights, view any profile regardless of privacy.
INSERT INTO auth.auth_permissions (name, description) VALUES
    ('contribution:view',     'Can view own contribution/reputation data and any public profile''s'),
    ('contribution:moderate', 'Can issue corrections, edit scoring weights, and view any profile regardless of privacy setting');

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_USER' AND p.name = 'contribution:view';

INSERT INTO auth.auth_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.auth_roles r, auth.auth_permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('contribution:view', 'contribution:moderate');
