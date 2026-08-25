-- ===========================================================================
-- SAANJHA 2.0: V29 MIGRATION (CHAT MODULE - ADD cleared_at COLUMN)
--
-- FIX: The ConversationMember entity declares a `cleared_at` column used by
-- ConversationService#clearHistory and MessageService#getHistory to support
-- per-user "clear chat history" — messages before the caller's cleared_at
-- timestamp are filtered out of history queries. The V24 schema definition
-- was missing this column, causing Hibernate schema validation to fail at
-- startup:
--
--   Schema-validation: missing column [cleared_at] in table
--   [cht.cht_conversation_members]
--
-- This migration adds the missing column as a nullable TIMESTAMPTZ (null
-- means "never cleared"), matching the entity's @Column(name = "cleared_at")
-- definition with no NOT NULL constraint.
-- ===========================================================================

ALTER TABLE cht.cht_conversation_members
    ADD COLUMN cleared_at TIMESTAMPTZ;
