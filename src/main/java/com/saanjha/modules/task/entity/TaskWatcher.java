package com.saanjha.modules.task.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A user watching a task for updates. Deliberately distinct from assignee — a watcher may never be assigned, and an assignee is not auto-added as a watcher (kept as two independent concepts, per the brief's explicit warning not to confuse them). */
@Entity
@Table(name = "tsk_watchers", schema = "tsk", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"task_id", "user_id"})
})
public class TaskWatcher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TaskWatcher() {
    }

    public TaskWatcher(UUID taskId, UUID userId) {
        this.taskId = taskId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
