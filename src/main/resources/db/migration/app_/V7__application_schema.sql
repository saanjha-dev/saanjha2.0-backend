-- ===========================================================================
-- SAANJHA 2.0: V7 MIGRATION (APPLICATION MODULE)
-- Owns: Application, Application Review, Application Notes, Application
-- Timeline/Audit. Does NOT own Projects, Users, or Team Members.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS app;

-- ---------------------------------------------------------------------------
-- 1. Aggregate Root: Applications
-- ---------------------------------------------------------------------------
CREATE TABLE app.app_applications (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id            UUID NOT NULL, -- Logical link to prj.prj_projects(id)
    applicant_id          UUID NOT NULL, -- Logical link to auth.auth_users(id)
    status                VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    message               TEXT NOT NULL,
    preferred_role        VARCHAR(100),
    weekly_hours          INT,
    timezone              VARCHAR(50),

    reviewed_at           TIMESTAMPTZ,
    reviewed_by           UUID,
    decision_reason       VARCHAR(500),

    withdrawn_at          TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ NOT NULL,

    -- Optimistic locking: protects concurrent review actions (Spec: "Two owners
    -- reviewing simultaneously", "Applicant withdraws while owner accepts").
    version               BIGINT NOT NULL DEFAULT 0,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by            VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_app_status
        CHECK (status IN ('SUBMITTED','UNDER_REVIEW','SHORTLISTED','ACCEPTED','REJECTED','WITHDRAWN','EXPIRED')),
    CONSTRAINT chk_app_weekly_hours CHECK (weekly_hours IS NULL OR (weekly_hours >= 1 AND weekly_hours <= 80))
);

-- Enforces "one active application per (project, applicant)" at the DB level,
-- not just in application code (Spec: "Duplicate Applications", "Applying twice").
-- A user MAY have multiple historical (terminal-status) rows for the same
-- project — e.g. rejected, then re-applied after the cooldown — but never two
-- simultaneously live ones.
CREATE UNIQUE INDEX uq_app_active_application
    ON app.app_applications (project_id, applicant_id)
    WHERE status IN ('SUBMITTED','UNDER_REVIEW','SHORTLISTED');

CREATE INDEX idx_app_applications_project ON app.app_applications (project_id, status);
CREATE INDEX idx_app_applications_applicant ON app.app_applications (applicant_id, status);

-- Powers the expiration sweep: only scans rows that are still open and overdue.
CREATE INDEX idx_app_applications_expiry
    ON app.app_applications (expires_at)
    WHERE status IN ('SUBMITTED','UNDER_REVIEW','SHORTLISTED');

-- ---------------------------------------------------------------------------
-- 2. Internal Reviewer Notes (never visible to the applicant)
-- ---------------------------------------------------------------------------
CREATE TABLE app.app_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES app.app_applications(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL,
    note            VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_notes_application ON app.app_notes (application_id, created_at);

-- ---------------------------------------------------------------------------
-- 3. Append-only Status Transition Ledger ("Application Timeline"/Audit)
-- ---------------------------------------------------------------------------
CREATE TABLE app.app_status_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID NOT NULL REFERENCES app.app_applications(id) ON DELETE CASCADE,
    from_status     VARCHAR(20) NOT NULL,
    to_status       VARCHAR(20) NOT NULL,
    changed_by      UUID NOT NULL, -- applicant, reviewing lead, or the SYSTEM actor for expirations
    reason          VARCHAR(500),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_status_log_application ON app.app_status_log (application_id, changed_at);
