package com.saanjha.modules.chat.entity;

/** OWNER is set once, at conversation creation (or synced from Team Lead for
 * PROJECT_TEAM/PROJECT_ANNOUNCEMENTS via LeadershipTransferredEvent). ADMIN
 * can moderate but not dissolve/transfer ownership. MEMBER is the default. */
public enum MemberRole {
    OWNER,
    ADMIN,
    MEMBER
}
