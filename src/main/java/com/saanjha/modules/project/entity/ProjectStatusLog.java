package com.saanjha.modules.project.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record of every lifecycle transition a project undergoes.
 * Deliberately NOT a BaseAuditEntity: this table is insert-only by design
 * (rows are never updated), so tracking updatedAt/updatedBy would be noise.
 *
 * changedBy carries the well-known SYSTEM_ACTOR_ID for transitions triggered
 * by scheduled jobs (e.g. the ghosting sweep) rather than a human request.
 */
@Entity
@Table(name = "prj_status_log", schema = "prj")
public class ProjectStatusLog {

    public static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "from_status", nullable = false, length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(length = 255)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    protected ProjectStatusLog() {
        // JPA
    }

    public ProjectStatusLog(UUID projectId, ProjectStatus from, ProjectStatus to, UUID changedBy, String reason) {
        this.projectId = projectId;
        this.fromStatus = from.name();
        this.toStatus = to.name();
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
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
