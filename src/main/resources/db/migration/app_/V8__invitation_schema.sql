-- ===========================================================================
-- SAANJHA 2.0: V8 MIGRATION (APPLICATION MODULE - INVITATIONS)
-- Invitations are a second recruitment entry point owned by the Application
-- module. They deliberately do NOT reference app_applications: an accepted
-- invitation never becomes an Application row, it goes straight to an
-- InvitationAcceptedEvent for the Team module to consume.
-- ===========================================================================

CREATE TABLE app.app_invitations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID NOT NULL,          -- Logical link to prj.prj_projects(id)
    invited_user_id  UUID NOT NULL,          -- Logical link to auth.auth_users(id)
    invited_by       UUID NOT NULL,          -- The project Lead who sent it
    preferred_role   VARCHAR(100),
    message          TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'SENT',

    responded_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ NOT NULL,

    version          BIGINT NOT NULL DEFAULT 0,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by       VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_inv_status CHECK (status IN ('SENT','ACCEPTED','DECLINED','EXPIRED','REVOKED'))
);

-- At most one LIVE invitation per (project, invited user) at a time — a Lead
-- must revoke or wait out the existing one before re-inviting.
CREATE UNIQUE INDEX uq_inv_active_invitation
    ON app.app_invitations (project_id, invited_user_id)
    WHERE status = 'SENT';

CREATE INDEX idx_inv_invitations_project ON app.app_invitations (project_id, status);
CREATE INDEX idx_inv_invitations_invitee ON app.app_invitations (invited_user_id, status);

CREATE INDEX idx_inv_invitations_expiry
    ON app.app_invitations (expires_at)
    WHERE status = 'SENT';
