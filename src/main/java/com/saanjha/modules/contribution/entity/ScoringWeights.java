package com.saanjha.modules.contribution.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * "Never hardcode business scoring" — every base weight lives here, not as
 * a Java constant. Versioned: editing weights creates a new version rather
 * than mutating the old one, so historical ledger entries (which each
 * record which version scored them) remain fully explainable even after
 * weights change — re-deriving "why did this task score 25 last year" never
 * requires guessing what the weights used to be.
 */
@Entity
@Table(name = "con_scoring_weights", schema = "con")
public class ScoringWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_type", nullable = false, length = 30)
    private ContributionType contributionType;

    @Column(name = "base_weight", nullable = false)
    private double baseWeight;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private String createdBy;

    protected ScoringWeights() {
    }

    public ScoringWeights(int version, ContributionType contributionType, double baseWeight, String createdBy) {
        this.version = version;
        this.contributionType = contributionType;
        this.baseWeight = baseWeight;
        this.createdBy = createdBy;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public ContributionType getContributionType() {
        return contributionType;
    }

    public double getBaseWeight() {
        return baseWeight;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
