package com.saanjha.modules.notification.entity;

/**
 * Identifies which {@link com.saanjha.modules.notification.provider.NotificationProvider}
 * implementation actually handled (or attempted) a given {@link ProviderAttempt}.
 * Persisted as a plain string column, not a DB enum, so a new provider never
 * needs a migration to become attributable in the audit trail.
 */
public enum ProviderName {
    /** Wraps sumitshresht/notificationhub-java. Never depended on directly outside {@code provider}. */
    NOTIFICATION_HUB,
    /** Fallback for EMAIL only, via the existing {@code shared.notification.EmailService}'s JavaMailSender bean. */
    SMTP,
    /** IN_APP's only provider - a local DB write, no network call, effectively always available. */
    IN_APP_STORE,
    /** Fallback for WEBHOOK, a direct signed HTTP POST bypassing the SDK entirely. */
    DIRECT_WEBHOOK,
    /** Last-resort fallback for every channel: logs at WARN and marks the attempt "delivered" so orchestration never wedges. */
    CONSOLE
}
