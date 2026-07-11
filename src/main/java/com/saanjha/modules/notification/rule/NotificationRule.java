package com.saanjha.modules.notification.rule;

import com.saanjha.modules.notification.entity.NotificationCategory;
import com.saanjha.modules.notification.entity.NotificationChannel;
import com.saanjha.modules.notification.entity.NotificationPriority;

import java.util.Set;

/**
 * The declarative policy for one {@link NotificationEventType}: which
 * category it belongs to (for category-level muting), how urgent it is, and
 * which channels it dispatches on by default (before the recipient's own
 * {@code NotificationPreference} filters/reorders them). Recipient
 * resolution is deliberately NOT part of this record - every event shapes
 * its payload differently (some name the recipient {@code userId}, some
 * {@code assigneeId}, some need a roster fan-out), so that stays explicit,
 * readable code in {@code NotificationEventListener} rather than a
 * reflection-based generic resolver that would be unreadable and fragile.
 */
public record NotificationRule(
        NotificationCategory category,
        NotificationPriority priority,
        Set<NotificationChannel> defaultChannels
) {
    public int maxAttemptsFor() {
        return switch (priority) {
            case CRITICAL -> 8;
            case HIGH -> 6;
            case NORMAL -> 4;
            case LOW -> 2;
        };
    }

    public java.time.Duration ttlFor() {
        return switch (priority) {
            case CRITICAL -> java.time.Duration.ofDays(7);
            case HIGH -> java.time.Duration.ofDays(5);
            case NORMAL -> java.time.Duration.ofDays(3);
            case LOW -> java.time.Duration.ofDays(1);
        };
    }
}
