package com.saanjha.modules.admin.entity;

/**
 * The catalog of governance actions Admin can take. Deliberately a single
 * flat enum shared across every target type rather than one enum per target
 * — {@code ModerationAction} rows are queried and audited as one unified
 * timeline (Section D.10 of the Admin brief: "Audit Timeline"), and a single
 * enum keeps that timeline queryable without a UNION across per-target
 * tables. Not every value is legal for every {@link ModerationTargetType};
 * legality is enforced in the owning service (e.g. {@code UserModerationService}
 * only ever writes the USER_* actions), not by a shared validator, since the
 * legal-action set per target is small and stable enough that a shared state
 * machine would add indirection without adding safety.
 */
public enum ModerationActionType {
    // User moderation
    USER_WARNED,
    USER_MUTED,
    USER_UNMUTED,
    USER_SHADOW_BANNED,
    USER_SHADOW_UNBANNED,
    USER_SUSPENDED,
    USER_UNSUSPENDED,
    USER_BANNED,
    USER_UNBANNED,
    USER_ROLE_GRANTED,
    USER_ROLE_REVOKED,

    // Project moderation (overlay actions; see ProjectModerationService javadoc
    // for which of these change Project's own state machine vs. Admin's overlay)
    PROJECT_LOCKED,
    PROJECT_UNLOCKED,
    PROJECT_HIDDEN,
    PROJECT_UNHIDDEN,
    PROJECT_FEATURED,
    PROJECT_UNFEATURED,
    PROJECT_ARCHIVED_BY_ADMIN,

    // Team moderation (thin wrapper over Team's own already-admin-capable endpoints)
    TEAM_LOCKED,
    TEAM_UNLOCKED,
    TEAM_DISSOLVED,
    TEAM_MEMBER_SUSPENDED,
    TEAM_MEMBER_REINSTATED,

    // Chat moderation (thin wrapper — Chat owns its own moderation_actions table;
    // this is Admin's unified-timeline mirror of an action taken through Chat's
    // own already-admin-capable `chat:moderate` endpoints)
    CHAT_MESSAGE_REMOVED,
    CHAT_CONVERSATION_LOCKED,
    CHAT_CONVERSATION_UNLOCKED,

    // Content moderation / reports
    REPORT_REVIEWED,
    REPORT_DISMISSED,
    REPORT_UPHELD
}
