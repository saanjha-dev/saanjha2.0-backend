package com.saanjha.modules.application.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record of every status transition an application
 * undergoes — the "Application Timeline" the brief asks for, mirroring the
 * pattern already established by the Project module's ProjectStatusLog.
 */
@Entity
@Table(name = "app_status_log", schema = "app")
public class ApplicationStatusLog {

    public static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "from_status", nullable = false, length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    protected ApplicationStatusLog() {
        // JPA
    }

    public ApplicationStatusLog(UUID applicationId, ApplicationStatus from, ApplicationStatus to, UUID changedBy, String reason) {
        this.applicationId = applicationId;
        this.fromStatus = from.name();
        this.toStatus = to.name();
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
