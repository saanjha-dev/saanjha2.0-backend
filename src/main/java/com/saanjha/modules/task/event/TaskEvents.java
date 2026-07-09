package com.saanjha.modules.task.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by the Task module. This is THE producer contract
 * for the future Contribution/Portfolio modules — per the brief's explicit
 * instruction, "Contribution should never read Task tables" and "Portfolio
 * should eventually know [...] without reading Task tables directly."
 * {@link TaskCompletedEvent} is therefore deliberately the richest payload
 * in this class: it carries everything the Contribution Engine's scoring
 * math (MES Section E: {@code C_base = (W_t * N_t) + (W_pr * N_pr) + (W_r * N_r)})
 * will need — complexity (storyPoints), estimate-vs-actual, and the
 * assignee's role — rather than the minimal shape used elsewhere, applying
 * the event-enrichment-chaining pattern (ADR-005) from day one instead of
 * retrofitting it once Contribution already depends on the gap.
 */
public final class TaskEvents {

    private TaskEvents() {
    }

    public record TaskCreatedEvent(UUID taskId, UUID projectId, UUID reporterId, String type, String priority, Instant occurredAt) {}

    /** Consumers: Notification (assignee alert), Chat (future, channel mention). */
    public record TaskAssignedEvent(UUID taskId, UUID projectId, UUID assigneeId, UUID assignedBy, Instant occurredAt) {}

    public record TaskUnassignedEvent(UUID taskId, UUID projectId, UUID previousAssigneeId, String reason, Instant occurredAt) {}

    public record TaskStartedEvent(UUID taskId, UUID projectId, UUID assigneeId, Instant occurredAt) {}

    public record TaskBlockedEvent(UUID taskId, UUID projectId, String reason, Instant occurredAt) {}

    public record TaskUnblockedEvent(UUID taskId, UUID projectId, Instant occurredAt) {}

    public record TaskMovedToReviewEvent(UUID taskId, UUID projectId, UUID assigneeId, Instant occurredAt) {}

    /**
     * THE Contribution Engine's primary input (MES Section E). Deliberately
     * enriched per this class's own javadoc: {@code complexity} is the
     * task's story points (nullable — not every team uses point estimation,
     * per the brief's "never force one methodology"), {@code priority} was
     * added after the Contribution module's own scoring engine identified
     * it as a required input it had no other way to obtain without reading
     * Task's schema (event-enrichment-chaining, ADR-005, applied to this
     * module's own event after the fact — exactly the kind of gap that
     * pattern exists to catch), {@code role} is a placeholder for the
     * assignee's contribution role once Team's `contributionTitle` is wired
     * through (currently always null — an honest placeholder, not a silent
     * omission), and {@code reviewedBy} captures who moved it out of
     * IN_REVIEW, satisfying the Contribution Engine's "Peer Reviews
     * conducted" (W_r * N_r) term without Contribution ever needing to
     * query Task's review history itself.
     */
    public record TaskCompletedEvent(
            UUID taskId, UUID projectId, UUID assigneeId, UUID reporterId,
            Integer complexity, String priority, String role, Double estimatedHours, double actualHours,
            UUID reviewedBy, Instant completedAt
    ) {}

    public record TaskReopenedEvent(UUID taskId, UUID projectId, UUID reopenedBy, Instant occurredAt) {}

    public record TaskArchivedEvent(UUID taskId, UUID projectId, Instant occurredAt) {}

    public record TaskRestoredEvent(UUID taskId, UUID projectId, Instant occurredAt) {}

    public record TaskCancelledEvent(UUID taskId, UUID projectId, String reason, Instant occurredAt) {}

    public record ChecklistCompletedEvent(UUID taskId, UUID projectId, Instant occurredAt) {}

    public record EstimateChangedEvent(UUID taskId, UUID projectId, Double previousEstimateHours, Double newEstimateHours, UUID changedBy, Instant occurredAt) {}

    public record TaskDependencyCreatedEvent(UUID taskId, UUID projectId, UUID relatedTaskId, String type, Instant occurredAt) {}

    public record TaskDependencyRemovedEvent(UUID taskId, UUID projectId, UUID relatedTaskId, String type, Instant occurredAt) {}
}
