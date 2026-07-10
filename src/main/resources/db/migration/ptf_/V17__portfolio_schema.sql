-- ===========================================================================
-- SAANJHA 2.0: V17 MIGRATION (PORTFOLIO MODULE)
-- Owns: verified portfolio entries (immutable), the live summary rollup,
-- badges, visibility settings, the activity timeline, and two internal-only
-- correlation/staging tables. Does NOT own Projects, Teams, Tasks,
-- Contribution, or Users — every fact here either arrives via an event
-- payload, a one-time ProjectSnapshotProvider call at generation time, or is
-- derived purely from events this module has already consumed.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS ptf;

-- ---------------------------------------------------------------------------
-- 1. Portfolio Entries — the aggregate root. One immutable row per
--    (user, project): verified proof that this user did real, completed
--    work on this project. No UPDATE path exists for the snapshot columns —
--    see PortfolioEntry's Javadoc for why there is deliberately no
--    correction/reversal mechanism here, unlike Contribution's ledger.
-- ---------------------------------------------------------------------------
CREATE TABLE ptf.ptf_entries (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                       UUID NOT NULL,       -- Logical link to auth.auth_users(id)
    project_id                    UUID NOT NULL,       -- Logical link to prj.prj_projects(id)

    -- Project snapshot, frozen via ProjectSnapshotProvider at generation time.
    project_title                 VARCHAR(150) NOT NULL,
    project_slug                  VARCHAR(180) NOT NULL,
    project_category              VARCHAR(30) NOT NULL,
    project_description_excerpt   TEXT,
    technologies                  JSONB NOT NULL DEFAULT '[]',

    -- Role/tenure snapshot, frozen via Team's TeamArchivedEvent.ArchivedMember.
    role                          VARCHAR(20) NOT NULL,
    was_lead                      BOOLEAN NOT NULL DEFAULT FALSE,
    contribution_title            VARCHAR(255),
    joined_at                     TIMESTAMPTZ,
    left_at                       TIMESTAMPTZ,
    tenure_days                   BIGINT,

    -- Contribution snapshot, accumulated live from ContributionRecordedEvent, frozen at generation time.
    contribution_score            DOUBLE PRECISION NOT NULL DEFAULT 0,
    tasks_completed               INT NOT NULL DEFAULT 0,
    reviews_given                 INT NOT NULL DEFAULT 0,

    verification_status           VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
    completed_at                  TIMESTAMPTZ NOT NULL,
    generated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_entry_role CHECK (role IN ('LEAD', 'MEMBER')),
    CONSTRAINT chk_entry_verification_status CHECK (verification_status IN ('VERIFIED')),
    CONSTRAINT uq_entry_user_project UNIQUE (user_id, project_id)
);

CREATE INDEX idx_entries_user ON ptf.ptf_entries (user_id, completed_at DESC);
CREATE INDEX idx_entries_project ON ptf.ptf_entries (project_id);
CREATE INDEX idx_entries_technologies ON ptf.ptf_entries USING GIN (technologies);

-- ---------------------------------------------------------------------------
-- 2. Portfolio Summary — a per-user, GLOBAL live rollup, incrementally
--    maintained (same design choice as Contribution's own con_summaries;
--    never recomputed by scanning every entry on read).
-- ---------------------------------------------------------------------------
CREATE TABLE ptf.ptf_summaries (
    user_id                UUID PRIMARY KEY,
    projects_completed     INT NOT NULL DEFAULT 0,
    leadership_stints      INT NOT NULL DEFAULT 0,
    total_verified_score   DOUBLE PRECISION NOT NULL DEFAULT 0,
    reliability_score      DOUBLE PRECISION,   -- Mirrored live from Contribution's ReputationProfile — never recomputed here.
    leadership_score       DOUBLE PRECISION,
    consistency_score      DOUBLE PRECISION,
    review_quality_score   DOUBLE PRECISION,
    last_generated_at      TIMESTAMPTZ,
    version                BIGINT NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 3. Badges — append-only, automatically awarded, never manually assignable
--    (no such endpoint exists at the API layer). The unique constraint is
--    the concurrency guard against a redelivered event double-awarding.
-- ---------------------------------------------------------------------------
CREATE TABLE ptf.ptf_badges (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    badge_type    VARCHAR(40) NOT NULL,
    evidence      JSONB NOT NULL DEFAULT '{}',
    awarded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_badge_type CHECK (badge_type IN (
        'PROJECT_LEADER', 'OPEN_SOURCE_CONTRIBUTOR', 'BACKEND_SPECIALIST', 'FRONTEND_SPECIALIST',
        'TASKS_COMPLETED_10', 'TASKS_COMPLETED_25', 'TASKS_COMPLETED_50', 'TASKS_COMPLETED_100',
        'TASKS_COMPLETED_250', 'TASKS_COMPLETED_500', 'TASKS_COMPLETED_1000'
    )),
    CONSTRAINT uq_badge_user_type UNIQUE (user_id, badge_type)
);

CREATE INDEX idx_badges_user ON ptf.ptf_badges (user_id, awarded_at DESC);

-- ---------------------------------------------------------------------------
-- 4. Visibility — one row per user. Absence of a row means PUBLIC (the
--    module's stated purpose is to be evidence recruiters consult by
--    default) — see PortfolioVisibility's Javadoc.
-- ---------------------------------------------------------------------------
CREATE TABLE ptf.ptf_visibility (
    user_id       UUID PRIMARY KEY,
    visibility    VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    share_token   VARCHAR(64) UNIQUE,
    version       BIGINT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_visibility_type CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'LINK_ONLY')),
    CONSTRAINT chk_share_token_only_when_link_only CHECK (
        (visibility = 'LINK_ONLY' AND share_token IS NOT NULL) OR
        (visibility <> 'LINK_ONLY' AND share_token IS NULL)
    )
);

-- ---------------------------------------------------------------------------
-- 5. Timeline — append-only chronological activity feed. Never updated,
--    never deleted.
-- ---------------------------------------------------------------------------
CREATE TABLE ptf.ptf_timeline (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL,
    project_id     UUID,
    event_type     VARCHAR(30) NOT NULL,
    description    VARCHAR(255) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_timeline_event_type CHECK (event_type IN
        ('JOINED_PROJECT', 'PROJECT_COMPLETED', 'LED_TEAM', 'MILESTONE_REACHED', 'BADGE_AWARDED'))
);

CREATE INDEX idx_timeline_user ON ptf.ptf_timeline (user_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- 6. Internal-only correlation/staging tables. NOT part of the public
--    aggregate/API surface — see PortfolioGenerationState's Javadoc for the
--    full ordering problem this pair of tables solves.
-- ---------------------------------------------------------------------------
CREATE TABLE ptf.ptf_generation_state (
    project_id                 UUID NOT NULL,
    user_id                    UUID NOT NULL,
    running_score              DOUBLE PRECISION NOT NULL DEFAULT 0,
    running_tasks_completed    INT NOT NULL DEFAULT 0,
    running_reviews_given      INT NOT NULL DEFAULT 0,
    role                       VARCHAR(20),
    contribution_title         VARCHAR(255),
    joined_at                  TIMESTAMPTZ,
    left_at                    TIMESTAMPTZ,
    tenure_days                BIGINT,
    team_data_arrived          BOOLEAN NOT NULL DEFAULT FALSE,
    generated                  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (project_id, user_id)
);

CREATE INDEX idx_generation_state_pending
    ON ptf.ptf_generation_state (project_id)
    WHERE generated = FALSE;

CREATE TABLE ptf.ptf_project_completion_signal (
    project_id      UUID PRIMARY KEY,
    completed_at    TIMESTAMPTZ NOT NULL,
    lead_user_id    UUID NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
