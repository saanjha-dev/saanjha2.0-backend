package com.saanjha.modules.contribution.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "con_snapshots", schema = "con")
public class ContributionSnapshot {

    public enum Reason {
        SCHEDULED, MILESTONE, MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_score", nullable = false)
    private double totalScore;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted;

    @Column(name = "reviews_given", nullable = false)
    private int reviewsGiven;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_reason", nullable = false, length = 30)
    private Reason snapshotReason;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt = Instant.now();

    protected ContributionSnapshot() {
    }

    public ContributionSnapshot(UUID userId, double totalScore, int tasksCompleted, int reviewsGiven, Reason reason) {
        this.userId = userId;
        this.totalScore = totalScore;
        this.tasksCompleted = tasksCompleted;
        this.reviewsGiven = reviewsGiven;
        this.snapshotReason = reason;
        this.capturedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public int getTasksCompleted() {
        return tasksCompleted;
    }

    public int getReviewsGiven() {
        return reviewsGiven;
    }

    public Reason getSnapshotReason() {
        return snapshotReason;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
