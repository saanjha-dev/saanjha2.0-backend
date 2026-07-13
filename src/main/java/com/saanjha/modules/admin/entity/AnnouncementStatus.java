package com.saanjha.modules.admin.entity;

/**
 * DRAFT -> SCHEDULED -> PUBLISHED -> EXPIRED, with CANCELLED reachable from
 * DRAFT or SCHEDULED only (an already-PUBLISHED announcement is withdrawn by
 * letting it expire or by an explicit unpublish, not by "cancelling" — see
 * AnnouncementService.unpublish).
 */
public enum AnnouncementStatus {
    DRAFT,
    SCHEDULED,
    PUBLISHED,
    EXPIRED,
    CANCELLED
}
