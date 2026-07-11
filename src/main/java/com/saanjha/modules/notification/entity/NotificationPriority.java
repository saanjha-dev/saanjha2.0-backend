package com.saanjha.modules.notification.entity;

/**
 * Drives two things: (1) whether quiet-hours/digest deferral applies at all
 * (CRITICAL always bypasses both - see {@code NotificationPreferenceService}),
 * and (2) DLQ/retry budget (CRITICAL gets more attempts before EXPIRED).
 */
public enum NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}
