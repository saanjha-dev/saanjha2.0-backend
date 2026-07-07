package com.saanjha.modules.task.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tsk_labels", schema = "tsk", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"task_id", "name"})
})
public class TaskLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false, length = 50)
    private String name;

    /** SYSTEM (platform-defined, e.g. "good-first-task") or PROJECT (Lead-defined per project). "Future global labels" per the brief is a values-only extension of this enum, not a schema change. */
    @Column(nullable = false, length = 20)
    private String scope = "PROJECT";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TaskLabel() {
    }

    public TaskLabel(Task task, String name, String scope) {
        this.task = task;
        this.name = name;
        this.scope = scope;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public String getName() {
        return name;
    }

    public String getScope() {
        return scope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
