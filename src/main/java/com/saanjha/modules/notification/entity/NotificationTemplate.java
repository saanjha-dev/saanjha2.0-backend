package com.saanjha.modules.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A rendering template for one (eventType, channel, locale). {@code
 * TemplateService} caches the active row per key in-memory (a plain
 * ConcurrentHashMap, invalidated on write) since templates change rarely and
 * are read on every single dispatch.
 * <p>
 * Versioned rather than overwritten-in-place: updating a template inserts a
 * new row and flips {@code isActive} on the old one, so
 * {@code ProviderAttempt}'s audit trail can always be correlated back to
 * "what content actually went out" even after a template edit - the same
 * "never silently mutate history" instinct as Portfolio's frozen entries.
 */
@Entity
@Table(name = "ntf_templates", schema = "ntf", indexes = {
        @Index(name = "idx_ntf_templates_lookup", columnList = "event_type, channel, locale, is_active")
})
@Getter
@Setter
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 15)
    private NotificationChannel channel;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "en";

    /** Null for channels without a distinct subject line (SMS/PUSH/IN_APP/WEBHOOK). Supports {{variable}} substitution. */
    @Column(name = "subject_template", length = 500)
    private String subjectTemplate;

    /** Supports {{variable}} substitution; may be Markdown/plain text for EMAIL (rendered to HTML at send time) or plain text otherwise. */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "action_url_template", length = 500)
    private String actionUrlTemplate;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static NotificationTemplate create(String eventType, NotificationChannel channel, String locale,
                                               String subjectTemplate, String bodyTemplate, String actionUrlTemplate) {
        NotificationTemplate t = new NotificationTemplate();
        t.eventType = eventType;
        t.channel = channel;
        t.locale = locale == null ? "en" : locale;
        t.subjectTemplate = subjectTemplate;
        t.bodyTemplate = bodyTemplate;
        t.actionUrlTemplate = actionUrlTemplate;
        return t;
    }
}
