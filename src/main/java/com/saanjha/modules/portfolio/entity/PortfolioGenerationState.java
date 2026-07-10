package com.saanjha.modules.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Solves a genuine cross-module ordering problem: a {@code PortfolioEntry}
 * needs data from THREE independent, un-ordered sources —
 * {@code ContributionRecordedEvent} (arrives repeatedly throughout the
 * project's lifetime, well before completion), {@code TeamArchivedEvent}
 * (arrives once, roster + role/tenure), and {@code ProjectCompletedEvent}
 * (arrives once, the gate that says "this was real, not abandoned").
 * Spring's in-process event delivery gives no ordering guarantee across
 * independent listeners reacting to different root publishers, so Portfolio
 * cannot assume any one of these arrives before another.
 *
 * This row is the staging ground: created lazily on whichever signal
 * arrives first for a given (project, user) pair, updated idempotently by
 * whichever arrives next, and consulted to decide "is it time to generate
 * the entry yet." Composite-keyed, so concurrent upserts for the same pair
 * are naturally serialized by the row lock rather than needing a separate
 * distributed lock.
 *
 * {@code generated = true} is the terminal marker: once set, both listener
 * paths treat further signals for this pair as no-ops (an entry is
 * immutable once created — see {@code PortfolioEntry}'s own Javadoc).
 */
@Entity
@Table(name = "ptf_generation_state", schema = "ptf")
@Getter
@Setter
@IdClass(PortfolioGenerationState.Key.class)
public class PortfolioGenerationState {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    // --- Running contribution accumulator (updated on every ContributionRecordedEvent for this pair) ---
    @Column(name = "running_score", nullable = false)
    private double runningScore = 0;

    @Column(name = "running_tasks_completed", nullable = false)
    private int runningTasksCompleted = 0;

    @Column(name = "running_reviews_given", nullable = false)
    private int runningReviewsGiven = 0;

    // --- Team-side data (arrives via TeamArchivedEvent.ArchivedMember) ---
    @Column(name = "role", length = 20)
    private String role;

    @Column(name = "contribution_title", length = 255)
    private String contributionTitle;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "tenure_days")
    private Long tenureDays;

    @Column(name = "team_data_arrived", nullable = false)
    private boolean teamDataArrived = false;

    // --- Project-side gate (arrives via a matching row in ptf_project_completion_signal) ---
    @Column(name = "generated", nullable = false)
    private boolean generated = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PortfolioGenerationState() {
        // JPA
    }

    public static PortfolioGenerationState blank(UUID projectId, UUID userId) {
        PortfolioGenerationState state = new PortfolioGenerationState();
        state.projectId = projectId;
        state.userId = userId;
        return state;
    }

    public void addContribution(double score, boolean isTaskCompletion, boolean isReview) {
        this.runningScore += score;
        if (isTaskCompletion) {
            this.runningTasksCompleted++;
        }
        if (isReview) {
            this.runningReviewsGiven++;
        }
        this.updatedAt = Instant.now();
    }

    public void applyTeamData(String role, String contributionTitle, Instant joinedAt, Instant leftAt, Long tenureDays) {
        this.role = role;
        this.contributionTitle = contributionTitle;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
        this.tenureDays = tenureDays;
        this.teamDataArrived = true;
        this.updatedAt = Instant.now();
    }

    public static class Key implements Serializable {
        private UUID projectId;
        private UUID userId;

        public Key() {
        }

        public Key(UUID projectId, UUID userId) {
            this.projectId = projectId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(projectId, key.projectId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, userId);
        }
    }
}
