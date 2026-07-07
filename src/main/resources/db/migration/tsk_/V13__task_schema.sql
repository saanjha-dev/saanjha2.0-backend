-- ===========================================================================
-- SAANJHA 2.0: V13 MIGRATION (TASK MODULE)
-- Owns: Task, Checklist Items, Labels, Dependencies, Watchers, Attachment
-- metadata, History (audit), Activity (feed). Does NOT own Projects, Team,
-- Chat, Notifications, Portfolio, or User Profiles.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS tsk;

-- ---------------------------------------------------------------------------
-- 1. Aggregate Root: Tasks
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_tasks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL, -- Logical link to prj.prj_projects(id)

    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    type                VARCHAR(20) NOT NULL,
    priority            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status              VARCHAR(20) NOT NULL DEFAULT 'BACKLOG',

    reporter_id         UUID NOT NULL, -- Logical link to auth.auth_users(id); who created it
    assignee_id         UUID,          -- Logical link to auth.auth_users(id); single assignee today

    story_points        INT,
    estimated_hours      DOUBLE PRECISION,
    actual_hours         DOUBLE PRECISION NOT NULL DEFAULT 0,
    due_date            TIMESTAMPTZ,

    blocked_reason      VARCHAR(500),

    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ,

    version             BIGINT NOT NULL DEFAULT 0,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by          VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_task_type CHECK (type IN ('FEATURE','BUG','CHORE','DOCUMENTATION','RESEARCH','DESIGN','INFRASTRUCTURE')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT','CRITICAL')),
    CONSTRAINT chk_task_status CHECK (status IN ('BACKLOG','TODO','IN_PROGRESS','BLOCKED','IN_REVIEW','DONE','CANCELLED','DUPLICATE','ARCHIVED')),
    CONSTRAINT chk_task_story_points CHECK (story_points IS NULL OR story_points >= 0),
    CONSTRAINT chk_task_estimated_hours CHECK (estimated_hours IS NULL OR estimated_hours >= 0),
    CONSTRAINT chk_task_actual_hours CHECK (actual_hours >= 0)
);

CREATE INDEX idx_task_project ON tsk.tsk_tasks (project_id, status);
CREATE INDEX idx_task_assignee ON tsk.tsk_tasks (assignee_id, status);
CREATE INDEX idx_task_reporter ON tsk.tsk_tasks (reporter_id);

-- Powers the hard-cap-at-3-IN_PROGRESS rule (MES H.2 #7): counts an
-- assignee's currently in-flight work without a full table scan.
CREATE INDEX idx_task_assignee_in_progress
    ON tsk.tsk_tasks (assignee_id)
    WHERE status = 'IN_PROGRESS';

-- Full-text search surface (title + description), matching the GIN/tsvector
-- pattern the MES describes for Discovery — Task's own "Future full-text
-- search" placeholder uses the same technique for consistency.
CREATE INDEX idx_task_search_text ON tsk.tsk_tasks
    USING GIN (to_tsvector('english', title || ' ' || COALESCE(description, '')));

-- ---------------------------------------------------------------------------
-- 2. Checklist Items (no separate "Checklist" wrapper entity — a task has
--    at most one checklist, so items reference the task directly)
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_checklist_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    text            VARCHAR(500) NOT NULL,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    position        INT NOT NULL,
    completed_at    TIMESTAMPTZ,
    completed_by    UUID,
    version         BIGINT NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by      VARCHAR(255) DEFAULT 'SYSTEM'
);

CREATE INDEX idx_checklist_task ON tsk.tsk_checklist_items (task_id, position);

-- ---------------------------------------------------------------------------
-- 3. Labels
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_labels (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id     UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    name        VARCHAR(50) NOT NULL,
    scope       VARCHAR(20) NOT NULL DEFAULT 'PROJECT',

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_label_scope CHECK (scope IN ('SYSTEM','PROJECT')),
    CONSTRAINT uq_task_label UNIQUE (task_id, name)
);

CREATE INDEX idx_label_task ON tsk.tsk_labels (task_id);
CREATE INDEX idx_label_name ON tsk.tsk_labels (LOWER(name));

-- ---------------------------------------------------------------------------
-- 4. Dependencies (self-referential, within the same project only)
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_dependencies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id             UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    related_task_id     UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    type                VARCHAR(20) NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID NOT NULL,

    CONSTRAINT chk_dependency_type CHECK (type IN ('BLOCKS','BLOCKED_BY','DUPLICATE_OF','RELATES_TO','PARENT','CHILD')),
    CONSTRAINT chk_dependency_not_self CHECK (task_id <> related_task_id),
    CONSTRAINT uq_task_dependency UNIQUE (task_id, related_task_id, type)
);

CREATE INDEX idx_dependency_task ON tsk.tsk_dependencies (task_id);
CREATE INDEX idx_dependency_related ON tsk.tsk_dependencies (related_task_id);

-- ---------------------------------------------------------------------------
-- 5. Watchers
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_watchers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id     UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_task_watcher UNIQUE (task_id, user_id)
);

CREATE INDEX idx_watcher_task ON tsk.tsk_watchers (task_id);
CREATE INDEX idx_watcher_user ON tsk.tsk_watchers (user_id);

-- ---------------------------------------------------------------------------
-- 6. Attachment Metadata (Task never owns file storage or bytes)
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_attachments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id             UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    file_name           VARCHAR(255) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    storage_url         VARCHAR(1000) NOT NULL, -- Points at Cloudinary/S3; Task stores the reference, never the bytes
    checksum            VARCHAR(128) NOT NULL,
    virus_scan_status   VARCHAR(20) NOT NULL DEFAULT 'SKIPPED',

    uploaded_by         UUID NOT NULL,
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_attachment_scan_status CHECK (virus_scan_status IN ('PENDING','CLEAN','INFECTED','SKIPPED')),
    CONSTRAINT chk_attachment_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_attachment_task ON tsk.tsk_attachments (task_id);

-- ---------------------------------------------------------------------------
-- 7. History (append-only audit: who changed what field, old -> new, why)
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    field_changed   VARCHAR(50) NOT NULL,
    old_value       VARCHAR(500),
    new_value       VARCHAR(500),
    changed_by      UUID NOT NULL,
    reason          VARCHAR(500),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_history_task ON tsk.tsk_history (task_id, changed_at);

-- ---------------------------------------------------------------------------
-- 8. Activity (append-only user feed — distinct from History; narrative,
--    not field-diff audit)
-- ---------------------------------------------------------------------------
CREATE TABLE tsk.tsk_activity (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID NOT NULL REFERENCES tsk.tsk_tasks(id) ON DELETE CASCADE,
    activity_type   VARCHAR(30) NOT NULL,
    actor_id        UUID NOT NULL,
    summary         VARCHAR(500) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_activity_task ON tsk.tsk_activity (task_id, occurred_at);
