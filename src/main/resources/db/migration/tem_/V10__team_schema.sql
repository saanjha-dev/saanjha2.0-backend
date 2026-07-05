-- ===========================================================================
-- SAANJHA 2.0: V10 MIGRATION (TEAM MODULE)
-- Owns: Team, Membership, Membership History, Leadership Transfer, Team
-- Settings, Team Metrics. Does NOT own Projects, Applications, Invitations,
-- Tasks, Channels, or Messages.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS tem;

-- ---------------------------------------------------------------------------
-- 1. Aggregate Root: Teams (1:1 with a Project)
-- ---------------------------------------------------------------------------
CREATE TABLE tem.tem_teams (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id               UUID NOT NULL, -- Logical link to prj.prj_projects(id); unique below

    status                   VARCHAR(20) NOT NULL DEFAULT 'CREATED',

    -- Settings stored as JSONB deliberately (see TeamSettings.java): lets new
    -- settings be added later without a schema migration, while the handful
    -- that matter today are still strongly typed at the application layer.
    settings                 JSONB NOT NULL DEFAULT '{}',

    -- Metrics: maintained as incrementally-updated counters at write time
    -- (O(1) per transition), never recomputed from history on read. "Eventually
    -- consistent" here means "not read-time-expensive," not "asynchronously lagged."
    current_member_count     INT NOT NULL DEFAULT 0,
    former_member_count      INT NOT NULL DEFAULT 0,
    leadership_change_count  INT NOT NULL DEFAULT 0,
    average_tenure_days      DOUBLE PRECISION NOT NULL DEFAULT 0,
    active_since             TIMESTAMPTZ,
    locked_at                TIMESTAMPTZ,
    archived_at              TIMESTAMPTZ,
    dissolved_at             TIMESTAMPTZ,
    dissolution_reason       VARCHAR(500),

    -- Optimistic + pessimistic locking target for every roster mutation
    -- (leadership transfer touches 2 rows atomically; concurrent accepts on
    -- the last slot must serialize against each other). See Section 12 of
    -- the approved architecture spec for why Team itself is the lock target.
    version                  BIGINT NOT NULL DEFAULT 0,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by                VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_team_status CHECK (status IN ('CREATED','ACTIVE','LOCKED','ARCHIVED','DISSOLVED')),
    CONSTRAINT chk_team_member_counts CHECK (current_member_count >= 0 AND former_member_count >= 0)
);

-- Enforces the 1:1 relationship with Project at the DB level, and doubles as
-- the idempotency guard for duplicate ProjectPublishedEvent delivery: a
-- find-or-create listener that races against itself will have the loser fail
-- this constraint rather than create a second Team for the same project.
CREATE UNIQUE INDEX uq_team_project ON tem.tem_teams (project_id);
CREATE INDEX idx_team_status ON tem.tem_teams (status);

-- ---------------------------------------------------------------------------
-- 2. Membership (the roster)
-- ---------------------------------------------------------------------------
CREATE TABLE tem.tem_memberships (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id            UUID NOT NULL REFERENCES tem.tem_teams(id) ON DELETE CASCADE,
    user_id            UUID NOT NULL, -- Logical link to auth.auth_users(id)

    role               VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Provenance, captured once at creation and never reconstructed later.
    joined_via         VARCHAR(20) NOT NULL,
    source_reference_id UUID, -- the Application or Invitation id that produced this row, if any

    contribution_title VARCHAR(100),

    joined_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at            TIMESTAMPTZ,
    removed_by         UUID,
    removal_reason     VARCHAR(500),

    version            BIGINT NOT NULL DEFAULT 0,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by         VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_membership_role CHECK (role IN ('LEAD','MEMBER')),
    CONSTRAINT chk_membership_status CHECK (status IN ('ACTIVE','LEFT','REMOVED','SUSPENDED','ARCHIVED')),
    CONSTRAINT chk_membership_joined_via CHECK (joined_via IN ('APPLICATION','INVITATION','MANUAL','MIGRATION','REJOINED'))
);

-- At most one live (ACTIVE or SUSPENDED) membership per (team, user) — a
-- suspended member still occupies their seat, they just can't act.
CREATE UNIQUE INDEX uq_membership_live_user
    ON tem.tem_memberships (team_id, user_id)
    WHERE status IN ('ACTIVE','SUSPENDED');

-- Exactly one ACTIVE Lead per team. This is the constraint that makes
-- "no leader" / "multiple leaders" structurally impossible, not just
-- application-code-checked.
CREATE UNIQUE INDEX uq_membership_single_active_lead
    ON tem.tem_memberships (team_id)
    WHERE role = 'LEAD' AND status = 'ACTIVE';

-- Idempotency guard for duplicate ApplicationAcceptedEvent/InvitationAcceptedEvent
-- delivery: a retried event that already produced a membership must be a safe no-op.
CREATE UNIQUE INDEX uq_membership_source_reference
    ON tem.tem_memberships (source_reference_id)
    WHERE source_reference_id IS NOT NULL;

CREATE INDEX idx_membership_team ON tem.tem_memberships (team_id, status);
CREATE INDEX idx_membership_user ON tem.tem_memberships (user_id, status);

-- ---------------------------------------------------------------------------
-- 3. Append-only Membership History ("who joined/left/why", never deleted)
-- ---------------------------------------------------------------------------
CREATE TABLE tem.tem_membership_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id         UUID NOT NULL REFERENCES tem.tem_teams(id) ON DELETE CASCADE,
    membership_id   UUID NOT NULL,
    user_id         UUID NOT NULL,
    event_type      VARCHAR(30) NOT NULL,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20) NOT NULL,
    from_role       VARCHAR(20),
    to_role         VARCHAR(20),
    actor_id        UUID NOT NULL, -- who caused this change (self, a Lead, or the SYSTEM actor)
    reason          VARCHAR(500),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_history_event_type CHECK (event_type IN
        ('JOINED','LEFT','REMOVED','SUSPENDED','REINSTATED','ROLE_CHANGED','ARCHIVED_WITH_TEAM'))
);

CREATE INDEX idx_history_team ON tem.tem_membership_history (team_id, occurred_at);
CREATE INDEX idx_history_user ON tem.tem_membership_history (user_id, occurred_at);
CREATE INDEX idx_history_membership ON tem.tem_membership_history (membership_id, occurred_at);
