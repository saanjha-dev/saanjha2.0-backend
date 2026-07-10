package com.saanjha.modules.portfolio.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately its own tiny table rather than a column bolted onto
 * {@link PortfolioGenerationState}: {@code ProjectCompletedEvent} is
 * project-scoped (fires once, doesn't enumerate members), while generation
 * state rows are (project, user)-scoped and only start existing once
 * {@code TeamArchivedEvent}'s roster or a {@code ContributionRecordedEvent}
 * creates them. If {@code TeamArchivedEvent} hasn't arrived yet when
 * {@code ProjectCompletedEvent} does, there may be ZERO generation-state
 * rows to update — this table is what lets the later-arriving
 * {@code TeamArchivedEvent} discover "oh, this project already completed"
 * without Portfolio needing to re-query Project synchronously.
 */
@Entity
@Table(name = "ptf_project_completion_signal", schema = "ptf")
public class ProjectCompletionSignal {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "lead_user_id", nullable = false)
    private UUID leadUserId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    protected ProjectCompletionSignal() {
        // JPA
    }

    public static ProjectCompletionSignal create(UUID projectId, Instant completedAt, UUID leadUserId) {
        ProjectCompletionSignal signal = new ProjectCompletionSignal();
        signal.projectId = projectId;
        signal.completedAt = completedAt;
        signal.leadUserId = leadUserId;
        return signal;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getLeadUserId() {
        return leadUserId;
    }
}
