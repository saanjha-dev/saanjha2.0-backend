package com.saanjha.modules.chat.entity;

/**
 * The seven conversation shapes Chat supports (module brief's "CONVERSATION
 * TYPES" section). PROJECT_TEAM and PROJECT_ANNOUNCEMENTS are the two
 * auto-provisioned, project-scoped channels every Team gets for free;
 * DIRECT_MESSAGE/GROUP/SUPPORT are user-initiated; SYSTEM is reserved for a
 * platform-wide broadcast channel (not auto-created per project).
 */
public enum ConversationType {
    PROJECT_TEAM,
    PROJECT_ANNOUNCEMENTS,
    DIRECT_MESSAGE,
    GROUP,
    SYSTEM,
    SUPPORT
}
