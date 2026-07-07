package com.saanjha.modules.task.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tsk_activity", schema = "tsk")
public class TaskActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private TaskActivityType activityType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected TaskActivity() {
    }

    public TaskActivity(UUID taskId, TaskActivityType activityType, UUID actorId, String summary) {
        this.taskId = taskId;
        this.activityType = activityType;
        this.actorId = actorId;
        this.summary = summary;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public TaskActivityType getActivityType() {
        return activityType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
