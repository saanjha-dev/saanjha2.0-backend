package com.saanjha.modules.chat.entity;

/**
 * ACTIVE -> LEFT (self-service) or ACTIVE -> REMOVED (moderator action) are
 * both terminal for that stint but not for the row: re-adding the same user
 * (e.g. rejoining a project's team) transitions the existing row back to
 * ACTIVE rather than inserting a duplicate - see the migration's unique
 * index on (conversation_id, user_id). MUTED still receives/reads messages
 * but is blocked from sending by ChatSecurityGuard. BLOCKED is the
 * target-user-blocked-by-owner state, distinct from REMOVED in that a
 * BLOCKED user cannot be re-added by anyone but an OWNER/ADMIN explicitly
 * unblocking them first.
 */
public enum MemberStatus {
    ACTIVE,
    MUTED,
    LEFT,
    REMOVED,
    BLOCKED
}
