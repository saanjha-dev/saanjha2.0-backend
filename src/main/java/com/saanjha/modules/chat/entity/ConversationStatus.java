package com.saanjha.modules.chat.entity;

/**
 * ACTIVE -> LOCKED (read-only, e.g. following ProjectArchivedEvent) or
 * ACTIVE -> ARCHIVED (following ProjectCompletedEvent - history preserved,
 * no new members, no new messages). LOCKED differs from ARCHIVED in that a
 * conversation can be unlocked by an Admin; ARCHIVED is a terminal cascade
 * that mirrors the owning Team/Project's own terminal state.
 */
public enum ConversationStatus {
    ACTIVE,
    LOCKED,
    ARCHIVED
}
