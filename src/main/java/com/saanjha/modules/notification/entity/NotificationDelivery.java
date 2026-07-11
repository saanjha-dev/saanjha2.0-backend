package com.saanjha.modules.notification.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (notification, channel). This is the actual "outbox" row in
 * this design: {@code NotificationOrchestrationService} writes it QUEUED in
 * the same local transaction that creates the {@link Notification}, and
 * {@code NotificationDispatchService}/{@code NotificationRetryScheduler}
 * are the only things allowed to move it forward. A crash between "queued"
 * and "actually called a provider" loses nothing - the row is durable in
 * Postgres and the scheduler will pick it up on the next sweep, which is
 * exactly the property that makes provider/SDK unavailability something
 * this module degrades gracefully from rather than fails on (module brief's
 * "Critical Architectural Requirement").
 */
@Entity
@Table(name = "ntf_deliveries", schema = "ntf", indexes = {
        @Index(name = "idx_ntf_deliveries_dispatch_scan", columnList = "status, next_attempt_at"),
        @Index(name = "idx_ntf_deliveries_notification", columnList = "notification_id")
})
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 15)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private DeliveryMode mode = DeliveryMode.INSTANT;

    /** Which provider last attempted (or ultimately succeeded on) this delivery. Null until the first attempt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_provider", length = 20)
    private ProviderName lastProvider;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    /** The row is not eligible for dispatch until now - this is both the initial digest delay and the retry backoff clock. */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "recipient_address", length = 320)
    private String recipientAddress;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected NotificationDelivery() {
        // JPA
    }

    public static NotificationDelivery queue(UUID notificationId, NotificationChannel channel, DeliveryMode mode,
                                              int maxAttempts, Instant nextAttemptAt, Instant expiresAt,
                                              String recipientAddress) {
        NotificationDelivery d = new NotificationDelivery();
        d.notificationId = notificationId;
        d.channel = channel;
        d.mode = mode;
        d.status = DeliveryStatus.QUEUED;
        d.maxAttempts = maxAttempts;
        d.nextAttemptAt = nextAttemptAt;
        d.expiresAt = expiresAt;
        d.recipientAddress = recipientAddress;
        return d;
    }

    public void beginProcessing() {
        this.status = DeliveryStatus.PROCESSING;
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }

    public void markSent(ProviderName provider) {
        this.status = DeliveryStatus.SENT;
        this.lastProvider = provider;
        this.sentAt = Instant.now();
        this.updatedAt = this.sentAt;
    }

    public void markDelivered(ProviderName provider) {
        this.status = DeliveryStatus.DELIVERED;
        this.lastProvider = provider;
        if (this.sentAt == null) {
            this.sentAt = Instant.now();
        }
        this.deliveredAt = Instant.now();
        this.updatedAt = this.deliveredAt;
    }

    public void markRead() {
        this.status = DeliveryStatus.READ;
        this.readAt = Instant.now();
        this.updatedAt = this.readAt;
    }

    /** Schedules a retry with the given backoff, or moves to FAILED (dispatcher then hands off to DLQ) if attempts are exhausted. */
    public boolean scheduleRetryOrExhaust(String error, Instant nextAttempt) {
        this.lastError = truncate(error);
        this.updatedAt = Instant.now();
        if (this.attemptCount >= this.maxAttempts) {
            this.status = DeliveryStatus.FAILED;
            return false;
        }
        this.status = DeliveryStatus.RETRYING;
        this.nextAttemptAt = nextAttempt;
        return true;
    }

    public void expire() {
        this.status = DeliveryStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = DeliveryStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    // --- Getters (immutable-shape entity with explicit mutation methods above, not blanket setters) ---

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public NotificationChannel getChannel() { return channel; }
    public DeliveryStatus getStatus() { return status; }
    public DeliveryMode getMode() { return mode; }
    public ProviderName getLastProvider() { return lastProvider; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getLastError() { return lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getRecipientAddress() { return recipientAddress; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
