-- ===========================================================================
-- SAANJHA 2.0: V28 MIGRATION (CHAT MODULE - DIRECT CONVERSATION DEDUP)
--
-- FIX (Chat Rework, root cause of "duplicate direct conversations" /
-- "conversation creation is inconsistent"): unlike PROJECT_TEAM /
-- PROJECT_ANNOUNCEMENTS (guarded by uq_conv_project_type, see V24), a
-- DIRECT_MESSAGE conversation had NO database-level uniqueness guard at
-- all. ConversationService#createConversation always inserted a fresh row,
-- so re-opening "New message" with the same person - or two concurrent
-- requests for the same pair (double-click, two tabs) - created a second,
-- disconnected DM thread instead of resolving to the existing one. The
-- frontend's only defense was a client-side check against whatever the
-- sidebar happened to have already loaded, which is not race-safe and does
-- nothing for a first-time picker session.
--
-- This adds a canonical, order-independent pair of participant columns
-- (direct_user_low/high, with low always the lexicographically smaller
-- UUID of the two - see ConversationService#getOrCreateDirectConversation)
-- and a partial unique index over them, scoped to DIRECT_MESSAGE rows only
-- - the exact same "unique index is the real guard, app-level check is
-- just a fast path" posture V24's uq_conv_project_type and
-- getOrCreateProjectConversation already established for project chat.
-- ===========================================================================

ALTER TABLE cht.cht_conversations
    ADD COLUMN direct_user_low  UUID,
    ADD COLUMN direct_user_high UUID;

ALTER TABLE cht.cht_conversations
    ADD CONSTRAINT chk_conv_direct_pair_order
        CHECK (direct_user_low IS NULL OR direct_user_high IS NULL OR direct_user_low < direct_user_high);

-- Race-safe idempotency guard for getOrCreateDirectConversation: at most
-- one DIRECT_MESSAGE conversation per unordered participant pair. A racing
-- duplicate create (double-click, two browser tabs, retry after timeout)
-- loses this constraint at the DB level, exactly like uq_conv_project_type
-- - the service catches DataIntegrityViolationException and re-reads the
-- winning row rather than surfacing an error to either caller.
CREATE UNIQUE INDEX uq_conv_direct_pair ON cht.cht_conversations (direct_user_low, direct_user_high)
    WHERE type = 'DIRECT_MESSAGE';
