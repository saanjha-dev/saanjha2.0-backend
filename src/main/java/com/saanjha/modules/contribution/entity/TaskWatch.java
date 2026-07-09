package com.saanjha.modules.contribution.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal-only derived state, NOT part of the public API surface. Exists
 * solely so the anti-gaming checks (reassignment churn, reopen farming, and
 * "instant completion" velocity) can be computed without ever reading
 * Task's schema — every field here is built purely from consuming
 * TaskAssignedEvent/TaskStartedEvent/TaskReopenedEvent as they arrive.
 */
@Entity
@Table(name = "con_task_watch", schema = "con")
public class TaskWatch {

    @Id
    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "assignment_count", nullable = false)
    private int assignmentCount = 0;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount = 0;

    @Column(name = "first_assigned_at")
    private Instant firstAssignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TaskWatch() {
    }

    public static TaskWatch blank(UUID taskId) {
        TaskWatch watch = new TaskWatch();
        watch.taskId = taskId;
        return watch;
    }

    public void recordAssignment() {
        this.assignmentCount++;
        if (this.firstAssignedAt == null) {
            this.firstAssignedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void recordStarted() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void recordReopen() {
        this.reopenCount++;
        this.updatedAt = Instant.now();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public int getAssignmentCount() {
        return assignmentCount;
    }

    public int getReopenCount() {
        return reopenCount;
    }

    public Instant getFirstAssignedAt() {
        return firstAssignedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
