package com.saanjha.modules.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a {@link NotificationDelivery} lands when {@code
 * NotificationRetryScheduler} finds it FAILED with no attempts remaining.
 * This is the module brief's "Dead Letter Queue abstraction" - deliberately
 * a plain table, not a real broker DLQ (see the final report's Architectural
 * Decisions for why a DB-backed queue was chosen over introducing RabbitMQ
 * for this despite it already being configured in application.yml).
 * A human (via the admin endpoint) or an automated sweep can requeue an
 * entry, which clears {@code resolvedAt} back to null being irrelevant -
 * requeue creates a *new* {@link NotificationDelivery} row rather than
 * mutating this historical one, keeping this table an honest append-only log.
 */
@Entity
@Table(name = "ntf_dead_letters", schema = "ntf", indexes = {
        @Index(name = "idx_ntf_dlq_unresolved", columnList = "resolved_at")
})
public class NotificationDeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 15)
    private NotificationChannel channel;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_snapshot", nullable = false, columnDefinition = "jsonb")
    private String payloadSnapshot;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    protected NotificationDeadLetter() {
        // JPA
    }

    public static NotificationDeadLetter of(UUID deliveryId, UUID notificationId, NotificationChannel channel,
                                             String reason, String payloadSnapshot) {
        NotificationDeadLetter dl = new NotificationDeadLetter();
        dl.deliveryId = deliveryId;
        dl.notificationId = notificationId;
        dl.channel = channel;
        dl.reason = reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        dl.payloadSnapshot = payloadSnapshot != null ? payloadSnapshot : "{}";
        return dl;
    }

    public void resolve(UUID resolvedBy, String note) {
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        this.resolutionNote = note;
    }

    public UUID getId() { return id; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getNotificationId() { return notificationId; }
    public NotificationChannel getChannel() { return channel; }
    public String getReason() { return reason; }
    public String getPayloadSnapshot() { return payloadSnapshot; }
    public Instant getMovedAt() { return movedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
}
