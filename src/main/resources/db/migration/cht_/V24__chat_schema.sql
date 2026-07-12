-- ===========================================================================
-- SAANJHA 2.0: V24 MIGRATION (CHAT MODULE)
--
-- Owns: Conversation, Conversation Member, Message, Reaction, Read Receipt,
-- Attachment (metadata only), Pinned Message, Draft, Moderation Action,
-- Mention. Does NOT own Project, Team, Task, Portfolio, Contribution,
-- Notification, Discovery, or attachment binary storage.
--
-- Deliberately NOT persisted here (see architecture note in ChatModule
-- javadoc): Presence (Redis-backed, ephemeral, TTL-expired) and Typing
-- (WebSocket-only relay, never touches the DB). Both would be schema noise
-- for state that is correct-by-construction to drop on restart.
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS cht;

-- ---------------------------------------------------------------------------
-- 1. Conversations (Chat's aggregate root)
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_conversations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Logical links only (Boundary Rule: no FK across schemas). Both nullable:
    -- a DIRECT_MESSAGE/GROUP/SUPPORT conversation has neither.
    project_id            UUID,
    team_id               UUID,

    type                  VARCHAR(30) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    name                  VARCHAR(150),
    topic                 VARCHAR(500),

    -- Settings stored as JSONB, same convention as tem.tem_teams.settings:
    -- lets new settings (e.g. slow-mode, join-approval) be added later
    -- without a migration, while ConversationSettings.java strongly types
    -- the handful that matter today.
    settings              JSONB NOT NULL DEFAULT '{}',

    member_count          INT NOT NULL DEFAULT 0,
    message_count         BIGINT NOT NULL DEFAULT 0,
    last_message_at       TIMESTAMPTZ,
    last_message_preview  VARCHAR(200),

    archived_at           TIMESTAMPTZ,
    locked_at             TIMESTAMPTZ,

    -- Optimistic locking: member_count/message_count are hot-path counters
    -- touched on every send; version protects the rare concurrent settings
    -- update from clobbering a concurrent counter increment.
    version               BIGINT NOT NULL DEFAULT 0,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by            VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_conv_type CHECK (type IN (
        'PROJECT_TEAM','PROJECT_ANNOUNCEMENTS','DIRECT_MESSAGE','GROUP','SYSTEM','SUPPORT'
    )),
    CONSTRAINT chk_conv_status CHECK (status IN ('ACTIVE','ARCHIVED','LOCKED')),
    CONSTRAINT chk_conv_counts CHECK (member_count >= 0 AND message_count >= 0)
);

-- Auto-provisioning idempotency guard: at most one PROJECT_TEAM and one
-- PROJECT_ANNOUNCEMENTS conversation per project. A racing duplicate
-- TeamCreatedEvent delivery loses this constraint, not creates a twin.
CREATE UNIQUE INDEX uq_conv_project_type ON cht.cht_conversations (project_id, type)
    WHERE project_id IS NOT NULL AND type IN ('PROJECT_TEAM','PROJECT_ANNOUNCEMENTS');
CREATE INDEX idx_conv_team ON cht.cht_conversations (team_id) WHERE team_id IS NOT NULL;
CREATE INDEX idx_conv_status ON cht.cht_conversations (status);
CREATE INDEX idx_conv_last_message_at ON cht.cht_conversations (last_message_at DESC);

-- ---------------------------------------------------------------------------
-- 2. Conversation Members
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_conversation_members (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES cht.cht_conversations(id),
    user_id               UUID NOT NULL,

    role                  VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- O(1) unread-count cursor. Per-message read audit trail (for "seen by"
    -- lists) lives separately in cht_read_receipts; this is the hot-path
    -- summary so unread badges never require a COUNT(*) query.
    last_read_message_id  UUID,
    last_read_at          TIMESTAMPTZ,
    unread_count          INT NOT NULL DEFAULT 0,

    muted_until           TIMESTAMPTZ,

    joined_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at               TIMESTAMPTZ,
    removed_by            UUID,
    removal_reason        VARCHAR(500),

    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by            VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_member_role CHECK (role IN ('OWNER','ADMIN','MEMBER')),
    CONSTRAINT chk_member_status CHECK (status IN ('ACTIVE','MUTED','LEFT','REMOVED','BLOCKED')),
    CONSTRAINT chk_unread_count CHECK (unread_count >= 0)
);

-- One membership row per (conversation, user) ever - status transitions in
-- place rather than re-inserting, so a re-add after LEFT/REMOVED updates the
-- same row (preserves join/leave history via cht_moderation_actions instead
-- of duplicating rows).
CREATE UNIQUE INDEX uq_conv_member ON cht.cht_conversation_members (conversation_id, user_id);
CREATE INDEX idx_conv_member_user ON cht.cht_conversation_members (user_id, status);
CREATE INDEX idx_conv_member_conv_status ON cht.cht_conversation_members (conversation_id, status);

-- ---------------------------------------------------------------------------
-- 3. Messages
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_messages (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES cht.cht_conversations(id),
    sender_id             UUID, -- NULL for SYSTEM messages

    parent_message_id     UUID REFERENCES cht.cht_messages(id),

    type                  VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    status                VARCHAR(20) NOT NULL DEFAULT 'SENT',

    body                  TEXT,
    -- Structured payload for CODE (language), TASK_REFERENCE/PROJECT_REFERENCE/
    -- PORTFOLIO_REFERENCE (referenced entity id + a denormalized label snapshot
    -- so a rename elsewhere doesn't require rewriting history), and SYSTEM
    -- messages (event type + actor). Never an entity reference across schemas.
    metadata              JSONB NOT NULL DEFAULT '{}',

    -- Thread rollup, maintained incrementally on the parent at reply-write time.
    reply_count           INT NOT NULL DEFAULT 0,
    last_reply_at         TIMESTAMPTZ,

    search_vector         TSVECTOR,

    edited_at             TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ,
    deleted_by            UUID,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by            VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT chk_msg_type CHECK (type IN (
        'TEXT','MARKDOWN','CODE','IMAGE','VIDEO','FILE','LINK','SYSTEM',
        'TASK_REFERENCE','PROJECT_REFERENCE','PORTFOLIO_REFERENCE'
    )),
    CONSTRAINT chk_msg_status CHECK (status IN ('SENT','EDITED','DELETED'))
);

CREATE INDEX idx_msg_conv_created ON cht.cht_messages (conversation_id, created_at DESC);
CREATE INDEX idx_msg_parent ON cht.cht_messages (parent_message_id) WHERE parent_message_id IS NOT NULL;
CREATE INDEX idx_msg_sender ON cht.cht_messages (sender_id) WHERE sender_id IS NOT NULL;
CREATE INDEX idx_msg_search_vector ON cht.cht_messages USING GIN (search_vector);
CREATE INDEX idx_msg_conv_not_deleted ON cht.cht_messages (conversation_id, created_at DESC) WHERE deleted_at IS NULL;

-- Search vector maintained via trigger, same pattern as dsc.dsc_project_documents
-- (V22): keeps the write path a single INSERT/UPDATE, no application-level
-- tsvector construction to keep in sync.
CREATE FUNCTION cht.fn_messages_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', coalesce(NEW.body, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_messages_search_vector
    BEFORE INSERT OR UPDATE OF body ON cht.cht_messages
    FOR EACH ROW EXECUTE FUNCTION cht.fn_messages_search_vector_update();

-- ---------------------------------------------------------------------------
-- 4. Reactions
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_reactions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id            UUID NOT NULL REFERENCES cht.cht_messages(id),
    user_id               UUID NOT NULL,
    emoji                 VARCHAR(64) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- "Unique per user" (per module brief): one of a given emoji per user per message.
CREATE UNIQUE INDEX uq_reaction_msg_user_emoji ON cht.cht_reactions (message_id, user_id, emoji);
CREATE INDEX idx_reaction_message ON cht.cht_reactions (message_id);

-- ---------------------------------------------------------------------------
-- 5. Read Receipts (per-message granularity, audit/"seen by" trail)
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_read_receipts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES cht.cht_conversations(id),
    message_id            UUID NOT NULL REFERENCES cht.cht_messages(id),
    user_id               UUID NOT NULL,
    read_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_receipt_msg_user ON cht.cht_read_receipts (message_id, user_id);
CREATE INDEX idx_receipt_conv_user ON cht.cht_read_receipts (conversation_id, user_id);

-- ---------------------------------------------------------------------------
-- 6. Attachments (metadata only - Chat never owns binary storage)
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_attachments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id            UUID NOT NULL REFERENCES cht.cht_messages(id),

    filename              VARCHAR(500) NOT NULL,
    mime_type             VARCHAR(150) NOT NULL,
    checksum              VARCHAR(128) NOT NULL,
    size_bytes            BIGINT NOT NULL,

    -- Pluggable storage provider (Cloudinary today, reusing shared.storage;
    -- any future provider plugs in by adding an enum value + adapter, no
    -- schema change).
    storage_provider      VARCHAR(30) NOT NULL DEFAULT 'CLOUDINARY',
    storage_reference     VARCHAR(1000) NOT NULL,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_attachment_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_attachment_message ON cht.cht_attachments (message_id);

-- ---------------------------------------------------------------------------
-- 7. Pinned Messages
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_pinned_messages (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES cht.cht_conversations(id),
    message_id            UUID NOT NULL REFERENCES cht.cht_messages(id),
    pinned_by             UUID NOT NULL,
    pinned_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    unpinned_at           TIMESTAMPTZ,
    unpinned_by           UUID
);

-- Only one *active* pin per message at a time (re-pinning after an unpin is
-- allowed - a new row - but a message can't be simultaneously double-pinned).
CREATE UNIQUE INDEX uq_pin_active_message ON cht.cht_pinned_messages (message_id) WHERE unpinned_at IS NULL;
CREATE INDEX idx_pin_conversation ON cht.cht_pinned_messages (conversation_id) WHERE unpinned_at IS NULL;

-- ---------------------------------------------------------------------------
-- 8. Drafts (per user, per conversation, optionally per thread)
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_drafts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES cht.cht_conversations(id),
    user_id               UUID NOT NULL,
    parent_message_id     UUID REFERENCES cht.cht_messages(id),
    body                  TEXT NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- At most one top-level draft and one draft per thread, per user. Two partial
-- unique indexes because Postgres treats NULLs as distinct in a plain UNIQUE
-- constraint, which would otherwise allow unlimited top-level drafts per user.
CREATE UNIQUE INDEX uq_draft_top_level ON cht.cht_drafts (conversation_id, user_id)
    WHERE parent_message_id IS NULL;
CREATE UNIQUE INDEX uq_draft_thread ON cht.cht_drafts (conversation_id, user_id, parent_message_id)
    WHERE parent_message_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 9. Mentions
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_mentions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id            UUID NOT NULL REFERENCES cht.cht_messages(id),
    conversation_id       UUID NOT NULL REFERENCES cht.cht_conversations(id),
    mentioned_user_id     UUID NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mention_user ON cht.cht_mentions (mentioned_user_id, created_at DESC);
CREATE INDEX idx_mention_message ON cht.cht_mentions (message_id);

-- ---------------------------------------------------------------------------
-- 10. Moderation Actions (append-only audit trail)
-- ---------------------------------------------------------------------------
CREATE TABLE cht.cht_moderation_actions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES cht.cht_conversations(id),
    message_id            UUID REFERENCES cht.cht_messages(id),
    target_user_id        UUID,
    action_type           VARCHAR(30) NOT NULL,
    reason                VARCHAR(1000),
    actor_id              UUID NOT NULL, -- MembershipHistory.SYSTEM_ACTOR_ID convention reused for event-driven actions
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_moderation_action_type CHECK (action_type IN (
        'DELETE_MESSAGE','MUTE_MEMBER','REMOVE_MEMBER','LOCK_CONVERSATION','UNLOCK_CONVERSATION','BLOCK_USER','UNBLOCK_USER'
    ))
);

CREATE INDEX idx_moderation_conv ON cht.cht_moderation_actions (conversation_id, created_at DESC);
