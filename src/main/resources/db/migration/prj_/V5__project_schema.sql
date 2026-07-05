-- ===========================================================================
-- SAANJHA 2.0: V5 MIGRATION (PROJECT MODULE)
-- 1. Create 'prj' schema, decoupled from 'auth' and 'usr'
-- 2. Core aggregate: projects, structural requirements, discovery tags
-- 3. Append-only status transition ledger for audit/debugging
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS prj;

-- ---------------------------------------------------------------------------
-- 1. Aggregate Root: Projects
-- ---------------------------------------------------------------------------
CREATE TABLE prj.prj_projects (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_user_id           UUID NOT NULL, -- Logical link to auth.auth_users(id)
    title                  VARCHAR(150) NOT NULL,
    slug                   VARCHAR(180) NOT NULL,
    description            TEXT NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    category               VARCHAR(30) NOT NULL,
    visibility             VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    max_team_size          INT NOT NULL,
    current_team_size      INT NOT NULL DEFAULT 1,
    recruiting_started_at  TIMESTAMPTZ,
    team_locked_at         TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    archived_at            TIMESTAMPTZ,
    archived_reason        VARCHAR(255),

    -- Optimistic locking: protects concurrent status transitions / scope edits
    version                BIGINT NOT NULL DEFAULT 0,

    -- BaseAuditEntity columns
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by             VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_prj_status
        CHECK (status IN ('DRAFT','RECRUITING','IN_PROGRESS','COMPLETED','ARCHIVED')),
    CONSTRAINT chk_prj_category
        CHECK (category IN ('WEB','MOBILE','AI_ML','BACKEND','DEVOPS','HACKATHON','OPEN_SOURCE','OTHER')),
    CONSTRAINT chk_prj_visibility
        CHECK (visibility IN ('PUBLIC','INVITE_ONLY')),
    CONSTRAINT chk_prj_team_size
        CHECK (max_team_size >= 1 AND max_team_size <= 50),
    CONSTRAINT chk_prj_current_team_size
        CHECK (current_team_size >= 1 AND current_team_size <= max_team_size)
);

-- Vanity/public URL slug must be globally unique; case-insensitive lookups
CREATE UNIQUE INDEX idx_prj_projects_slug ON prj.prj_projects (LOWER(slug));

-- Ownership dashboard queries ("my projects")
CREATE INDEX idx_prj_projects_lead ON prj.prj_projects (lead_user_id);

-- Status-scoped listing queries (public recruiting feed, admin views)
CREATE INDEX idx_prj_projects_status ON prj.prj_projects (status);

-- Powers the "Ghosting Leads" scheduled sweep (H.2 #6): only scans RECRUITING rows
CREATE INDEX idx_prj_projects_recruiting_started
    ON prj.prj_projects (recruiting_started_at)
    WHERE status = 'RECRUITING';

-- ---------------------------------------------------------------------------
-- 2. Structural Requirements (skills the Lead is recruiting for)
-- ---------------------------------------------------------------------------
CREATE TABLE prj.prj_requirements (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        UUID NOT NULL REFERENCES prj.prj_projects(id) ON DELETE CASCADE,
    skill_name        VARCHAR(100) NOT NULL,
    skill_level       VARCHAR(20) NOT NULL,
    slots_available   INT NOT NULL,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by        VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_prj_req_level CHECK (skill_level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    CONSTRAINT chk_prj_req_slots CHECK (slots_available >= 1 AND slots_available <= 20),
    CONSTRAINT uq_prj_req_skill UNIQUE (project_id, skill_name)
);

CREATE INDEX idx_prj_requirements_project ON prj.prj_requirements (project_id);

-- Feeds the future Discovery module's skill-match index
CREATE INDEX idx_prj_requirements_skill_name ON prj.prj_requirements (LOWER(skill_name));

-- ---------------------------------------------------------------------------
-- 3. Discovery Tags
-- ---------------------------------------------------------------------------
CREATE TABLE prj.prj_tags (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES prj.prj_projects(id) ON DELETE CASCADE,
    tag_name     VARCHAR(50) NOT NULL,

    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by   VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT uq_prj_tag UNIQUE (project_id, tag_name)
);

CREATE INDEX idx_prj_tags_project ON prj.prj_tags (project_id);
CREATE INDEX idx_prj_tags_name ON prj.prj_tags (LOWER(tag_name));

-- ---------------------------------------------------------------------------
-- 4. Status Transition Ledger (append-only audit trail)
-- ---------------------------------------------------------------------------
CREATE TABLE prj.prj_status_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL REFERENCES prj.prj_projects(id) ON DELETE CASCADE,
    from_status   VARCHAR(20) NOT NULL,
    to_status     VARCHAR(20) NOT NULL,
    changed_by    UUID NOT NULL, -- auth user id, or the well-known SYSTEM actor for scheduled jobs
    reason        VARCHAR(255),
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Powers "show me the history of this project" and admin audit screens
CREATE INDEX idx_prj_status_log_project ON prj.prj_status_log (project_id, changed_at);
