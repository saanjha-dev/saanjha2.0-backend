package com.saanjha.modules.task.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The Task module's aggregate root — the Work Management Engine's core unit.
 * Ownership is expressed via {@code projectId}, a logical (non-FK) reference
 * to the Project module. Task never caches a {@code teamId}: every
 * membership/authorization check goes live through
 * {@code TeamService.isActiveMember(projectId, userId)} rather than a
 * denormalized copy, since Task has no hot-path reason to avoid that one
 * extra lookup the way Project's `@PreAuthorize` checks did for Team.
 */
@Entity
@Table(name = "tsk_tasks", schema = "tsk")
@Getter
@Setter
public class Task extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "task_key", length = 20)
    private String taskKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.BACKLOG;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "story_points")
    private Integer storyPoints;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "actual_hours", nullable = false)
    private double actualHours = 0.0;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isMutable() {
        return status != TaskStatus.ARCHIVED;
    }

    public boolean isAssigned() {
        return assigneeId != null;
    }
}
