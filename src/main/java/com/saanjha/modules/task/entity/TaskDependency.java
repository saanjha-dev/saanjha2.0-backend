package com.saanjha.modules.task.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A directed edge between two tasks. BLOCKS/BLOCKED_BY are stored as two
 * rows (one on each task) so both sides can be queried directly without a
 * join-direction check — see TaskService for how creating one auto-creates
 * its inverse.
 */
@Entity
@Table(name = "tsk_dependencies", schema = "tsk")
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "related_task_id", nullable = false)
    private UUID relatedTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DependencyType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected TaskDependency() {
    }

    public TaskDependency(UUID taskId, UUID relatedTaskId, DependencyType type, UUID createdBy) {
        this.taskId = taskId;
        this.relatedTaskId = relatedTaskId;
        this.type = type;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getRelatedTaskId() {
        return relatedTaskId;
    }

    public DependencyType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
