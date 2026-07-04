package com.saanjha.modules.project.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain events published by the Project module. Only meaningful business
 * actions are represented here — CRUD noise (a title edit, a tag add) is
 * intentionally NOT published, per the "no events for CRUD" rule.
 */
public final class ProjectEvents {

    private ProjectEvents() {
    }

    /**
     * Fired the moment a project transitions DRAFT -> RECRUITING and becomes
     * visible in Search/Discovery.
     * Consumers: Discovery (indexes the listing), Notification (broadcasts to
     * users whose skills match the requirements — future enhancement).
     */
    public record ProjectPublishedEvent(
            UUID projectId,
            UUID leadUserId,
            String title,
            List<String> requiredSkills,
            Instant occurredAt
    ) {}

    /**
     * Fired on every legal state transition. This is a general-purpose signal
     * for any future module that cares about project lifecycle changes without
     * needing a dedicated event type (e.g. Task module unlocking the workspace
     * on IN_PROGRESS, Team module locking the roster on IN_PROGRESS).
     */
    public record ProjectStatusChangedEvent(
            UUID projectId,
            String fromStatus,
            String toStatus,
            UUID changedBy,
            Instant occurredAt
    ) {}

    /**
     * Fired when a project reaches COMPLETED.
     * Consumers (per Spec Section G): Team (disbands the roster), Portfolio
     * (generates verified proof-of-work entries for every contributor).
     *
     * NOTE — architectural gap identified during implementation: the master
     * spec's event catalog lists this event's payload as {@code projectId, teamList},
     * but team roster is owned by the Team bounded context, not Project. Having
     * Project populate teamList here would require it to reach into Team's data,
     * violating the module boundary rule. This event therefore carries only
     * data Project actually owns. When the Team module ships, it should listen
     * to this event and independently emit its own roster-bearing event (e.g.
     * TeamDisbandedEvent) for Portfolio to consume — see Future Extension Points.
     */
    public record ProjectCompletedEvent(
            UUID projectId,
            UUID leadUserId,
            Instant completedAt
    ) {}

    /**
     * Fired when a project is abandoned (manually by its Lead/an Admin, or
     * automatically by the ghosting sweep). Consumers: Notification (informs
     * pending applicants once the Application module exists), Discovery
     * (de-indexes the listing).
     */
    public record ProjectArchivedEvent(
            UUID projectId,
            String previousStatus,
            UUID archivedBy,
            String reason,
            Instant occurredAt
    ) {}
}
