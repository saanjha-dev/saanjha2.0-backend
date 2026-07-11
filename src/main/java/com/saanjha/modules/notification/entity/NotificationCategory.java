package com.saanjha.modules.notification.entity;

/**
 * Coarse grouping of event types, one level above the "per-event
 * preferences" the brief also asks for. A user can mute a whole category
 * (e.g. "TASK") or override a single event type within it
 * (see {@link NotificationPreference}'s two-tier design).
 */
public enum NotificationCategory {
    SECURITY,
    ACCOUNT,
    PROJECT,
    APPLICATION,
    INVITATION,
    TEAM,
    TASK,
    CONTRIBUTION,
    PORTFOLIO
}
