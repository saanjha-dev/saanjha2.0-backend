package com.saanjha.modules.task.entity;

import com.saanjha.shared.audit.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single checklist line item. No separate "Checklist" wrapper entity
 * exists — a Task has at most one checklist, so items reference the task
 * directly (same YAGNI reasoning as rejecting a "Workspace" entity in Team:
 * an empty 1:1 wrapper with no data of its own isn't worth the indirection).
 * Completion percentage is computed on read from these rows, not cached.
 */
@Entity
@Table(name = "tsk_checklist_items", schema = "tsk")
@Getter
@Setter
public class ChecklistItem extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(nullable = false)
    private int position;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Version
    @Column(nullable = false)
    private long version;
}
