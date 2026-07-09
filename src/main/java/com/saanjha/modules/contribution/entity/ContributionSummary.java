package com.saanjha.modules.contribution.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A per-user, GLOBAL rollup — read-side only, incrementally maintained the
 * same way Team's metrics are (O(1) update per ledger entry, never
 * recomputed by scanning the whole ledger on read). This is NEVER the
 * source of truth: if it and the ledger ever disagree, the ledger wins, and
 * this table can be entirely rebuilt by replaying it (see
 * {@code ContributionService.rebuildSummary}).
 */
@Entity
@Table(name = "con_summaries", schema = "con")
@Getter
@Setter
public class ContributionSummary {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "total_score", nullable = false)
    private double totalScore = 0;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted = 0;

    @Column(name = "reviews_given", nullable = false)
    private int reviewsGiven = 0;

    @Column(name = "leadership_stints", nullable = false)
    private int leadershipStints = 0;

    @Column(name = "tasks_abandoned", nullable = false)
    private int tasksAbandoned = 0;

    @Column(name = "last_contribution_at")
    private Instant lastContributionAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static ContributionSummary blank(UUID userId) {
        ContributionSummary summary = new ContributionSummary();
        summary.userId = userId;
        return summary;
    }

    public void apply(ContributionLedgerEntry entry) {
        this.totalScore += entry.getFinalScore();
        if (!entry.isReversal()) {
            switch (entry.getContributionType()) {
                case TASK_COMPLETION -> this.tasksCompleted++;
                case TASK_REVIEW -> this.reviewsGiven++;
                case LEADERSHIP -> this.leadershipStints++;
                case TASK_ABANDONED -> this.tasksAbandoned++;
                default -> { /* PLANNING/MENTORSHIP don't have a dedicated counter yet */ }
            }
        }
        this.lastContributionAt = entry.getOccurredAt();
        this.updatedAt = Instant.now();
    }
}
