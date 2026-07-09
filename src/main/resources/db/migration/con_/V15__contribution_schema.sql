-- ===========================================================================
-- SAANJHA 2.0: V15 MIGRATION (CONTRIBUTION MODULE)
-- Owns: the Contribution Ledger (immutable), Summaries, Reputation,
-- Snapshots, Scoring Weights (configurable, never hardcoded), and two
-- internal-only integrity-tracking tables. Does NOT own Tasks, Projects,
-- Teams, or Users — every fact here either arrives via an event payload or
-- is derived purely from events this module has already consumed.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS con;

-- ---------------------------------------------------------------------------
-- 1. The Ledger (immutable — no UPDATE path exists for scoring fields, ever;
--    corrections are new rows, never edits to old ones)
-- ---------------------------------------------------------------------------
CREATE TABLE con.con_ledger_entries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL,       -- Logical link to auth.auth_users(id)
    project_id           UUID,                -- Logical link to prj.prj_projects(id); nullable for platform-wide entries
    source_type          VARCHAR(30) NOT NULL,-- Which upstream event produced this (e.g. 'TASK_COMPLETED')
    source_reference_id  UUID NOT NULL,       -- The taskId/projectId/etc. that caused this entry — also the idempotency key

    contribution_type    VARCHAR(30) NOT NULL,
    context_task_type    VARCHAR(20),         -- Copy of the underlying Task's domain type (FEATURE/BUG/...), for display only

    base_score           DOUBLE PRECISION NOT NULL,
    complexity_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    quality_multiplier    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    leadership_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    final_score          DOUBLE PRECISION NOT NULL,

    -- The Explanation Engine's structured breakdown — never just a number.
    -- JSONB for the same reason Team's settings are JSONB: new scoring
    -- factors can be added to the breakdown without a schema migration.
    explanation          JSONB NOT NULL DEFAULT '[]',

    integrity_flag        VARCHAR(30) NOT NULL DEFAULT 'NONE',

    -- Compensating-entry correction chain (Stripe-ledger-style: never edit,
    -- only ever append a reversing/corrected pair, linked here).
    correction_of_entry_id UUID REFERENCES con.con_ledger_entries(id),
    is_reversal            BOOLEAN NOT NULL DEFAULT FALSE,

    scoring_weights_version INT NOT NULL,      -- Which ScoringWeights version produced this — required for "support future recalculation"

    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recorded_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ledger_contribution_type CHECK (contribution_type IN
        ('TASK_COMPLETION','TASK_REVIEW','LEADERSHIP','MENTORSHIP','PLANNING','TASK_ABANDONED')),
    CONSTRAINT chk_ledger_integrity_flag CHECK (integrity_flag IN
        ('NONE','SUSPICIOUS_VELOCITY','SELF_REVIEW','REASSIGNMENT_CHURN','REOPEN_FARMING'))
);

-- Idempotency guard: the same upstream event (identified by its own id) must
-- never produce two non-reversal ledger entries. Reversal/correction rows
-- are exempt (they intentionally reference the same source but are a
-- distinct, later fact).
CREATE UNIQUE INDEX uq_ledger_source_reference
    ON con.con_ledger_entries (source_reference_id, source_type)
    WHERE is_reversal = FALSE;

CREATE INDEX idx_ledger_user ON con.con_ledger_entries (user_id, occurred_at);
CREATE INDEX idx_ledger_project ON con.con_ledger_entries (project_id, occurred_at);
CREATE INDEX idx_ledger_type ON con.con_ledger_entries (contribution_type);
CREATE INDEX idx_ledger_integrity_flag
    ON con.con_ledger_entries (integrity_flag)
    WHERE integrity_flag <> 'NONE';

-- ---------------------------------------------------------------------------
-- 2. Contribution Summary — a per-user, GLOBAL rollup. Deliberately NOT
--    per-project: "My Contributions" is naturally cross-project, and
--    project-scoped/team-scoped views are served by a live aggregate query
--    over the ledger instead (same choice Task made for its own analytics —
--    a second per-project rollup table isn't justified by read frequency).
--    Always reconstructable from the ledger; never the source of truth.
-- ---------------------------------------------------------------------------
CREATE TABLE con.con_summaries (
    user_id                UUID PRIMARY KEY,
    total_score            DOUBLE PRECISION NOT NULL DEFAULT 0,
    tasks_completed        INT NOT NULL DEFAULT 0,
    reviews_given          INT NOT NULL DEFAULT 0,
    leadership_stints      INT NOT NULL DEFAULT 0,
    tasks_abandoned        INT NOT NULL DEFAULT 0,
    last_contribution_at   TIMESTAMPTZ,
    version                BIGINT NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 3. Reputation — explicitly separate from raw contribution (per the
--    brief's own instruction: "Never mix reputation with raw contribution").
--    Dimensions with no current data source (communication, mentorship) are
--    left NULL, not fabricated as zero — see ReputationProfile's javadoc.
-- ---------------------------------------------------------------------------
CREATE TABLE con.con_reputation_profiles (
    user_id                UUID PRIMARY KEY,
    reliability_score      DOUBLE PRECISION,
    leadership_score       DOUBLE PRECISION,
    consistency_score      DOUBLE PRECISION,
    review_quality_score   DOUBLE PRECISION,
    communication_score    DOUBLE PRECISION, -- Reserved: no data source until Chat exists
    mentorship_score       DOUBLE PRECISION, -- Reserved: no data source until a mentorship-tracking concept exists
    version                BIGINT NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- 4. Snapshots — periodic point-in-time freezes of a user's summary, so
--    trend charts don't require re-summing the ledger for historical points
--    as it grows toward millions of rows.
-- ---------------------------------------------------------------------------
CREATE TABLE con.con_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    total_score     DOUBLE PRECISION NOT NULL,
    tasks_completed INT NOT NULL,
    reviews_given   INT NOT NULL,
    snapshot_reason VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    captured_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_snapshot_reason CHECK (snapshot_reason IN ('SCHEDULED','MILESTONE','MANUAL'))
);

CREATE INDEX idx_snapshot_user ON con.con_snapshots (user_id, captured_at);

-- ---------------------------------------------------------------------------
-- 5. Scoring Weights — configurable, versioned. "Never hardcode business
--    scoring": every base weight and multiplier input lives here, editable
--    by Admin, with each ledger entry recording which version scored it.
-- ---------------------------------------------------------------------------
CREATE TABLE con.con_scoring_weights (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version              INT NOT NULL,
    contribution_type    VARCHAR(30) NOT NULL,
    base_weight          DOUBLE PRECISION NOT NULL,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT uq_scoring_weight_version_type UNIQUE (version, contribution_type)
);

CREATE INDEX idx_scoring_weights_active
    ON con.con_scoring_weights (contribution_type)
    WHERE active = TRUE;

-- ---------------------------------------------------------------------------
-- 6. Internal-only integrity-tracking tables. NOT part of the public
--    aggregate/API surface — pure derived state built purely from consuming
--    events, so the anti-gaming checks below never need to read Task's or
--    Team's schemas.
-- ---------------------------------------------------------------------------
CREATE TABLE con.con_task_watch (
    task_id             UUID PRIMARY KEY, -- Logical link to tsk.tsk_tasks(id)
    assignment_count    INT NOT NULL DEFAULT 0,
    reopen_count        INT NOT NULL DEFAULT 0,
    first_assigned_at   TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE con.con_project_team_size (
    project_id      UUID PRIMARY KEY, -- Logical link to prj.prj_projects(id)
    current_size    INT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
