-- ===========================================================================
-- SAANJHA 2.0: V22 MIGRATION (DISCOVERY MODULE)
-- Owns: read-optimized search documents, trending snapshots, technology
-- rollups, search history/suggestions/saved searches, and a recommendation
-- cache. Does NOT own Project, User, Team, Task, Contribution, or Portfolio
-- data — every fact here arrives via an event payload
-- (ProjectDiscoveryUpdatedEvent, UserDiscoveryUpdatedEvent,
-- ContributionRecordedEvent/ReputationUpdatedEvent, PortfolioEvents,
-- TeamEvents) and is projected asynchronously. No table here has a foreign
-- key into another module's schema, consistent with the boundary rule.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS dsc;

-- ---------------------------------------------------------------------------
-- 1. Project Search Documents — one row per project Discovery has ever heard
--    about via ProjectDiscoveryUpdatedEvent. is_indexed=false once the
--    project leaves RECRUITING/IN_PROGRESS (archived/completed); the row is
--    kept, not deleted, so "related projects" and historical ranking signals
--    still have something to read.
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_project_documents (
    project_id           UUID PRIMARY KEY,             -- Logical link to prj.prj_projects(id)
    lead_user_id         UUID NOT NULL,

    title                VARCHAR(150) NOT NULL,
    slug                 VARCHAR(180) NOT NULL,
    description_excerpt  TEXT,
    category             VARCHAR(30) NOT NULL,
    visibility           VARCHAR(20) NOT NULL,
    status               VARCHAR(20) NOT NULL,

    required_skills      JSONB NOT NULL DEFAULT '[]',
    tags                 JSONB NOT NULL DEFAULT '[]',

    max_team_size        INT NOT NULL DEFAULT 0,
    current_team_size    INT NOT NULL DEFAULT 0,

    is_indexed           BOOLEAN NOT NULL DEFAULT TRUE,
    popularity_score     DOUBLE PRECISION NOT NULL DEFAULT 0,
    published_at         TIMESTAMPTZ,

    search_vector        TSVECTOR,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prj_doc_status ON dsc.dsc_project_documents (status, is_indexed);
CREATE INDEX idx_prj_doc_category ON dsc.dsc_project_documents (category);
CREATE INDEX idx_prj_doc_required_skills ON dsc.dsc_project_documents USING GIN (required_skills);
CREATE INDEX idx_prj_doc_tags ON dsc.dsc_project_documents USING GIN (tags);
CREATE INDEX idx_prj_doc_search_vector ON dsc.dsc_project_documents USING GIN (search_vector);

CREATE OR REPLACE FUNCTION dsc.trg_project_doc_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.category, '')), 'B') ||
        setweight(to_tsvector('english', array_to_string(
            (SELECT array_agg(x) FROM jsonb_array_elements_text(NEW.required_skills) x), ' ')), 'B') ||
        setweight(to_tsvector('english', array_to_string(
            (SELECT array_agg(x) FROM jsonb_array_elements_text(NEW.tags) x), ' ')), 'C') ||
        setweight(to_tsvector('english', coalesce(NEW.description_excerpt, '')), 'D');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_project_doc_search_vector
    BEFORE INSERT OR UPDATE ON dsc.dsc_project_documents
    FOR EACH ROW EXECUTE FUNCTION dsc.trg_project_doc_search_vector();

-- ---------------------------------------------------------------------------
-- 2. Developer Search Documents — one row per user Discovery has heard about
--    via UserDiscoveryUpdatedEvent, enriched incrementally by Contribution
--    and Portfolio events. is_deleted mirrors the User module's soft delete.
--    availability_status / remote_preference are deliberate extension
--    points: no upstream module publishes this data today (see
--    architecture-review.md discussion) — columns exist so a future
--    UserAvailabilityChangedEvent (or equivalent) can populate them without
--    a schema migration, but they are NEVER filtered/ranked on until an
--    event actually sets them.
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_developer_documents (
    user_id                  UUID PRIMARY KEY,          -- Logical link to auth.auth_users(id)

    display_name             VARCHAR(150),
    unique_handle            VARCHAR(50),
    headline                 VARCHAR(255),
    bio_excerpt              TEXT,
    location                 VARCHAR(150),
    experience_level         VARCHAR(30),

    skills                   JSONB NOT NULL DEFAULT '[]',   -- [{skillName, skillLevel, isVerified}]
    interests                JSONB NOT NULL DEFAULT '[]',

    profile_score            INT NOT NULL DEFAULT 0,
    projects_completed       INT NOT NULL DEFAULT 0,

    reliability_score        DOUBLE PRECISION,
    leadership_score         DOUBLE PRECISION,
    consistency_score        DOUBLE PRECISION,
    review_quality_score     DOUBLE PRECISION,
    contribution_total_score DOUBLE PRECISION NOT NULL DEFAULT 0,

    portfolio_badge_count    INT NOT NULL DEFAULT 0,
    portfolio_visibility     VARCHAR(20),

    -- Extension points (see class/migration-level note above). NULL == "unknown", never a filter default.
    availability_status      VARCHAR(30),
    remote_preference        VARCHAR(30),

    is_deleted               BOOLEAN NOT NULL DEFAULT FALSE,
    search_vector             TSVECTOR,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dev_doc_experience ON dsc.dsc_developer_documents (experience_level);
CREATE INDEX idx_dev_doc_skills ON dsc.dsc_developer_documents USING GIN (skills);
CREATE INDEX idx_dev_doc_search_vector ON dsc.dsc_developer_documents USING GIN (search_vector);
CREATE INDEX idx_dev_doc_active ON dsc.dsc_developer_documents (is_deleted);

CREATE OR REPLACE FUNCTION dsc.trg_developer_doc_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.display_name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.unique_handle, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.headline, '')), 'B') ||
        setweight(to_tsvector('english', array_to_string(
            (SELECT array_agg(x->>'skillName') FROM jsonb_array_elements(NEW.skills) x), ' ')), 'B') ||
        setweight(to_tsvector('english', array_to_string(
            (SELECT array_agg(x) FROM jsonb_array_elements_text(NEW.interests) x), ' ')), 'C') ||
        setweight(to_tsvector('english', coalesce(NEW.bio_excerpt, '')), 'D');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_developer_doc_search_vector
    BEFORE INSERT OR UPDATE ON dsc.dsc_developer_documents
    FOR EACH ROW EXECUTE FUNCTION dsc.trg_developer_doc_search_vector();

-- ---------------------------------------------------------------------------
-- 3. Team Search Documents — lightweight, projected from Team's own events.
--    Required-skill matching for "teams recruiting for X" is served by
--    joining to dsc_project_documents on project_id at query time (both
--    tables live in this module's own schema, so this is not a cross-schema
--    join and does not violate the boundary rule).
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_team_documents (
    team_id            UUID PRIMARY KEY,
    project_id         UUID NOT NULL,
    founder_user_id    UUID NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_size       INT NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_team_doc_project ON dsc.dsc_team_documents (project_id);
CREATE INDEX idx_team_doc_status ON dsc.dsc_team_documents (status);

-- ---------------------------------------------------------------------------
-- 4. Technology Stats — aggregated rollup per normalized technology/skill
--    name, incrementally maintained (same pattern as Contribution's/Portfolio's
--    own summary rollups) rather than recomputed at request time.
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_technology_stats (
    technology_name       VARCHAR(100) PRIMARY KEY,
    project_count         INT NOT NULL DEFAULT 0,
    developer_count        INT NOT NULL DEFAULT 0,
    verified_developer_count INT NOT NULL DEFAULT 0,
    trending_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_computed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tech_stats_trending ON dsc.dsc_technology_stats (trending_score DESC);

-- ---------------------------------------------------------------------------
-- 5. Trending Snapshots — periodically recomputed by the Trending Engine
--    (scheduled job), never at request time. entity_key is the project/user
--    id (as text) or the technology name, depending on entity_type.
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_trending_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(20) NOT NULL,   -- PROJECT, DEVELOPER, TEAM, TECHNOLOGY, SKILL
    entity_key      VARCHAR(255) NOT NULL,
    window_type     VARCHAR(10) NOT NULL,   -- DAILY, WEEKLY
    score           DOUBLE PRECISION NOT NULL,
    rank            INT NOT NULL,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_trending_entity_type CHECK (entity_type IN ('PROJECT', 'DEVELOPER', 'TEAM', 'TECHNOLOGY', 'SKILL')),
    CONSTRAINT chk_trending_window CHECK (window_type IN ('DAILY', 'WEEKLY'))
);

CREATE INDEX idx_trending_lookup ON dsc.dsc_trending_snapshots (entity_type, window_type, rank);
-- Keeps only the latest snapshot set queryable per (type, window) cheaply; the scheduler
-- deletes the previous batch inside the same transaction that inserts the new one.
CREATE INDEX idx_trending_computed_at ON dsc.dsc_trending_snapshots (computed_at DESC);

-- ---------------------------------------------------------------------------
-- 6. Search History — personal, per authenticated user only (never logged
--    for anonymous callers, since there is no user_id to attach it to and
--    Discovery does not maintain a session/device identity of its own).
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_search_history (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL,
    query_text     VARCHAR(500),
    filters        JSONB NOT NULL DEFAULT '{}',
    result_count   INT NOT NULL DEFAULT 0,
    searched_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_search_history_user ON dsc.dsc_search_history (user_id, searched_at DESC);

-- ---------------------------------------------------------------------------
-- 7. Saved Searches
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_saved_searches (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL,
    name           VARCHAR(150) NOT NULL,
    query_text     VARCHAR(500),
    filters        JSONB NOT NULL DEFAULT '{}',
    last_run_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saved_search_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_saved_search_user ON dsc.dsc_saved_searches (user_id);

-- ---------------------------------------------------------------------------
-- 8. Search Suggestions — autocomplete source, incrementally maintained from
--    indexed skills/technologies/project titles/developer handles.
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_search_suggestions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    term           VARCHAR(150) NOT NULL,
    entity_type    VARCHAR(20) NOT NULL,  -- SKILL, TECHNOLOGY, PROJECT_TITLE, DEVELOPER_HANDLE
    frequency      BIGINT NOT NULL DEFAULT 1,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_suggestion_term_type UNIQUE (term, entity_type),
    CONSTRAINT chk_suggestion_entity_type CHECK (entity_type IN ('SKILL', 'TECHNOLOGY', 'PROJECT_TITLE', 'DEVELOPER_HANDLE'))
);

CREATE INDEX idx_suggestion_prefix ON dsc.dsc_search_suggestions (entity_type, term text_pattern_ops);

-- ---------------------------------------------------------------------------
-- 9. Recommendation Cache — the Recommendation Engine writes here so
--    request-time reads never recompute from scratch; a background refresh
--    (see RecommendationEngine's Javadoc) keeps it warm.
-- ---------------------------------------------------------------------------
CREATE TABLE dsc.dsc_recommendation_cache (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL,
    recommendation_type   VARCHAR(30) NOT NULL, -- PROJECTS, DEVELOPERS, TEAMMATES, TECHNOLOGIES
    payload               JSONB NOT NULL DEFAULT '[]',
    generated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recommendation_user_type UNIQUE (user_id, recommendation_type),
    CONSTRAINT chk_recommendation_type CHECK (recommendation_type IN ('PROJECTS', 'DEVELOPERS', 'TEAMMATES', 'TECHNOLOGIES'))
);

CREATE INDEX idx_recommendation_expiry ON dsc.dsc_recommendation_cache (expires_at);
