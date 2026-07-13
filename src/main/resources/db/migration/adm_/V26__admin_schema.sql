-- ===========================================================================
-- SAANJHA 2.0: V26 MIGRATION (ADMIN / PLATFORM GOVERNANCE MODULE SCHEMA)
--
-- Owns 10 tables in the `adm` schema. No FK reaches into another module's
-- schema (Boundary Rule, Part 1) — every cross-module reference below
-- (target_id, actor_id, reporter_user_id, etc.) is a plain UUID column, not
-- a foreign key, resolved at read time via that module's own service.
--
-- adm_appeals.moderation_action_id IS a real FK — to adm_moderation_actions,
-- a table this same schema owns, so that's an ordinary same-schema FK, not a
-- boundary violation.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS adm;

-- ---------------------------------------------------------------------------
-- Technical audit ledger. Immutable by application contract (see
-- AdminAuditLogRepository — no update/delete method exists). No UPDATE/DELETE
-- grant is issued to the application role for this table, enforcing the
-- append-only contract at the database level as well as in code.
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_audit_log (
    id              UUID PRIMARY KEY,
    actor_id        UUID NOT NULL,
    actor_roles     VARCHAR(255),
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(30),
    target_id       UUID,
    old_value       TEXT,
    new_value       TEXT,
    reason          VARCHAR(1000),
    request_id      VARCHAR(100),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(500),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_adm_audit_log_actor ON adm.adm_audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_adm_audit_log_target ON adm.adm_audit_log (target_type, target_id, occurred_at DESC);
CREATE INDEX idx_adm_audit_log_request ON adm.adm_audit_log (request_id);
CREATE INDEX idx_adm_audit_log_occurred_at ON adm.adm_audit_log (occurred_at DESC);

-- ---------------------------------------------------------------------------
-- Domain-level moderation decisions. See ModerationAction's javadoc for the
-- split from adm_audit_log.
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_moderation_actions (
    id                  UUID PRIMARY KEY,
    target_type         VARCHAR(30) NOT NULL,
    target_id           UUID NOT NULL,
    action_type         VARCHAR(40) NOT NULL,
    actor_id            UUID NOT NULL,
    reason              VARCHAR(1000),
    evidence            TEXT,
    related_report_id   UUID,
    reversed            BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_adm_moderation_actions_target ON adm.adm_moderation_actions (target_type, target_id, created_at DESC);
CREATE INDEX idx_adm_moderation_actions_actor ON adm.adm_moderation_actions (actor_id, created_at DESC);
CREATE INDEX idx_adm_moderation_actions_created_at ON adm.adm_moderation_actions (created_at DESC);

-- ---------------------------------------------------------------------------
-- Reports (user-submitted).
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_reports (
    id                      UUID PRIMARY KEY,
    reporter_user_id        UUID NOT NULL,
    target_type             VARCHAR(30) NOT NULL,
    target_id               UUID NOT NULL,
    category                VARCHAR(40) NOT NULL,
    description             VARCHAR(2000),
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_moderator_id   UUID,
    resolution_notes        VARCHAR(2000),
    resolved_by             UUID,
    resolved_at             TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_adm_reports_status ON adm.adm_reports (status, created_at ASC);
CREATE INDEX idx_adm_reports_moderator ON adm.adm_reports (assigned_moderator_id, status);
CREATE INDEX idx_adm_reports_target ON adm.adm_reports (target_type, target_id, created_at DESC);
CREATE INDEX idx_adm_reports_reporter ON adm.adm_reports (reporter_user_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Appeals — same-schema FK into adm_moderation_actions (not a boundary
-- violation, both tables are owned by this module).
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_appeals (
    id                      UUID PRIMARY KEY,
    moderation_action_id    UUID NOT NULL REFERENCES adm.adm_moderation_actions (id),
    appellant_user_id       UUID NOT NULL,
    statement               VARCHAR(2000) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by              UUID,
    decision_notes          VARCHAR(2000),
    decided_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_adm_appeals_status ON adm.adm_appeals (status, created_at ASC);
CREATE INDEX idx_adm_appeals_appellant ON adm.adm_appeals (appellant_user_id, created_at DESC);
-- Only one open appeal per moderation action at a time (partial unique index,
-- enforced at the DB level rather than only in ContentModerationService).
CREATE UNIQUE INDEX uq_adm_appeals_open_per_action ON adm.adm_appeals (moderation_action_id)
    WHERE status IN ('PENDING', 'UNDER_REVIEW');

-- ---------------------------------------------------------------------------
-- Feature flags.
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_feature_flags (
    id                      UUID PRIMARY KEY,
    flag_key                VARCHAR(150) NOT NULL UNIQUE,
    description             VARCHAR(500),
    flag_type               VARCHAR(20) NOT NULL DEFAULT 'BOOLEAN',
    enabled                 BOOLEAN NOT NULL DEFAULT false,
    rollout_percentage      INTEGER,
    target_user_ids         TEXT,
    target_project_ids      TEXT,
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              UUID,
    CONSTRAINT chk_adm_feature_flags_rollout_pct CHECK (rollout_percentage IS NULL OR rollout_percentage BETWEEN 0 AND 100)
);

-- ---------------------------------------------------------------------------
-- Platform settings (limits, thresholds, retention policies, registration
-- controls, read-only/maintenance mode — see PlatformSettingsService).
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_platform_settings (
    id              UUID PRIMARY KEY,
    setting_key     VARCHAR(150) NOT NULL UNIQUE,
    setting_value   TEXT,
    value_type      VARCHAR(20) NOT NULL DEFAULT 'STRING',
    description     VARCHAR(500),
    version         BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID
);

-- ---------------------------------------------------------------------------
-- Announcements.
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_announcements (
    id              UUID PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    type            VARCHAR(30) NOT NULL DEFAULT 'INFO_BANNER',
    audience        VARCHAR(30) NOT NULL DEFAULT 'ALL',
    priority        VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    starts_at       TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_by      UUID NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_adm_announcements_status ON adm.adm_announcements (status, expires_at);
CREATE INDEX idx_adm_announcements_created_at ON adm.adm_announcements (created_at DESC);

-- ---------------------------------------------------------------------------
-- Internal moderator notes (never shown to the target user).
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_notes (
    id              UUID PRIMARY KEY,
    target_type     VARCHAR(30) NOT NULL,
    target_id       UUID NOT NULL,
    author_id       UUID NOT NULL,
    note            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_adm_notes_target ON adm.adm_notes (target_type, target_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Trust & Safety risk cache (see TrustScoreService — deliberately a simple
-- weighted counter, not a model; schema designed so a future ML scorer can
-- replace the recalculation logic without a schema change).
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_trust_scores (
    id                          UUID PRIMARY KEY,
    user_id                     UUID NOT NULL UNIQUE,
    score                       DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    risk_level                  VARCHAR(20) NOT NULL DEFAULT 'LOW',
    report_count                INTEGER NOT NULL DEFAULT 0,
    upheld_report_count         INTEGER NOT NULL DEFAULT 0,
    suspicious_activity_count   INTEGER NOT NULL DEFAULT 0,
    last_recalculated_at        TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_adm_trust_scores_risk_level ON adm.adm_trust_scores (risk_level);

-- ---------------------------------------------------------------------------
-- Dashboard snapshots (hourly rollup — see AdminDashboardService.captureSnapshot).
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_dashboard_snapshots (
    id                              UUID PRIMARY KEY,
    captured_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    open_reports                    BIGINT NOT NULL DEFAULT 0,
    pending_appeals                 BIGINT NOT NULL DEFAULT 0,
    active_suspensions              BIGINT NOT NULL DEFAULT 0,
    moderation_actions_last_24h     BIGINT NOT NULL DEFAULT 0,
    high_risk_users                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_adm_dashboard_snapshots_captured_at ON adm.adm_dashboard_snapshots (captured_at DESC);

-- ---------------------------------------------------------------------------
-- Project moderation overlay (lock/hide/feature — see ProjectModerationOverlay
-- javadoc for why this lives here rather than in the prj schema).
-- ---------------------------------------------------------------------------
CREATE TABLE adm.adm_project_overlays (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL UNIQUE,
    locked          BOOLEAN NOT NULL DEFAULT false,
    locked_reason   VARCHAR(1000),
    hidden          BOOLEAN NOT NULL DEFAULT false,
    hidden_reason   VARCHAR(1000),
    featured        BOOLEAN NOT NULL DEFAULT false,
    version         BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID
);
CREATE INDEX idx_adm_project_overlays_featured ON adm.adm_project_overlays (featured) WHERE featured = true;
CREATE INDEX idx_adm_project_overlays_hidden ON adm.adm_project_overlays (hidden) WHERE hidden = true;
