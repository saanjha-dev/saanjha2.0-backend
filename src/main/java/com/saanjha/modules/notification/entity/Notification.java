package com.saanjha.modules.notification.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The notification aggregate root - one row per (recipient, source domain
 * event), regardless of how many channels it ends up dispatching on. Actual
 * per-channel delivery state lives in {@link NotificationDelivery}, never
 * here; {@link #status} is a derived "best status across channels" summary
 * recomputed by {@code NotificationOrchestrationService} after every
 * delivery transition, kept for cheap list-view rendering
 * (GET /v1/notifications) without a join for the common case.
 * <p>
 * Idempotency is enforced at the DB constraint level (this codebase's
 * established convention - see Team's duplicate-membership guard, Portfolio's
 * badge engine): {@code (recipient_user_id, source_event_id)} is unique, so
 * a redelivered domain event (Spring in-process events are "at least once"
 * across a listener's own retries/exceptions, same as every other module's
 * listeners in this repo) can never create a second Notification for the
 * same fact.
 */
@Entity
@Table(name = "ntf_notifications", schema = "ntf", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ntf_recipient_source_event", columnNames = {"recipient_user_id", "source_event_id"})
})
@Getter
@Setter
public class Notification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    /** e.g. "APPLICATION_SUBMITTED" - matches the simple name of the upstream event record, uppercased-snake. */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private NotificationPriority priority;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /** Deep link the client can navigate to (e.g. "/projects/{id}/applications/{id}"). Nullable. */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /** Dedup + template-variable source: eventType-qualified natural key, e.g. "APPLICATION_SUBMITTED:{applicationId}". */
    @Column(name = "source_event_id", nullable = false, length = 150)
    private String sourceEventId;

    /** Raw event payload snapshot (JSON) - the template engine's variable source; also useful for support/debugging. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    /** Best status across this notification's deliveries; see class javadoc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.CREATED;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // JPA
    }

    public static Notification create(UUID recipientUserId, String eventType, NotificationCategory category,
                                       NotificationPriority priority, String title, String body, String actionUrl,
                                       String sourceEventId, String payloadJson) {
        Notification n = new Notification();
        n.recipientUserId = recipientUserId;
        n.eventType = eventType;
        n.category = category;
        n.priority = priority;
        n.title = title;
        n.body = body;
        n.actionUrl = actionUrl;
        n.sourceEventId = sourceEventId;
        n.payloadJson = payloadJson != null ? payloadJson : "{}";
        n.status = DeliveryStatus.CREATED;
        return n;
    }

    public void markRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
        this.status = DeliveryStatus.READ;
    }
}
