package com.saanjha.modules.task.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit ledger, generic across any mutable field — unlike
 * Project/Application/Team's status-only logs, Task has enough independently
 * meaningful mutable fields (status, assignee, priority) that a field/old/new
 * shape is more useful here than a status-only one. Distinct from
 * {@link TaskActivity}: this is the audit trail; that is the user feed.
 */
@Entity
@Table(name = "tsk_history", schema = "tsk")
public class TaskHistory {

    public static final UUID SYSTEM_ACTOR_ID = new UUID(0L, 0L);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "field_changed", nullable = false, length = 50)
    private String fieldChanged;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    protected TaskHistory() {
    }

    public TaskHistory(UUID taskId, String fieldChanged, String oldValue, String newValue, UUID changedBy, String reason) {
        this.taskId = taskId;
        this.fieldChanged = fieldChanged;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getFieldChanged() {
        return fieldChanged;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
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
