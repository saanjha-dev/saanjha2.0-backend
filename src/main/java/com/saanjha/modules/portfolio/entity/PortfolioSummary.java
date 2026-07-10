package com.saanjha.modules.portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A per-user, GLOBAL read-side rollup — same design choice as Contribution's
 * own {@code ContributionSummary}: incrementally maintained (O(1) update per
 * incoming event), never recomputed by scanning every {@code PortfolioEntry}
 * on read.
 *
 * {@code totalVerifiedScore} is a frozen-at-generation-time sum of each
 * entry's own {@code contributionScore} — it is intentionally NOT kept in
 * lockstep with Contribution's live ledger if a correction arrives after a
 * project's entries were already generated. See the module write-up's Known
 * Tradeoffs for why this is a deliberate choice, not an oversight.
 *
 * {@code reliabilityScore}/{@code leadershipScore}/{@code consistencyScore}/
 * {@code reviewQualityScore} mirror Contribution's {@code ReputationProfile}
 * live (via {@code ReputationUpdatedEvent}) — Portfolio never recomputes
 * these, only relays them, per the "Portfolio never calculates contribution"
 * rule.
 */
@Entity
@Table(name = "ptf_summaries", schema = "ptf")
@Getter
@Setter
public class PortfolioSummary {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "projects_completed", nullable = false)
    private int projectsCompleted = 0;

    @Column(name = "leadership_stints", nullable = false)
    private int leadershipStints = 0;

    @Column(name = "total_verified_score", nullable = false)
    private double totalVerifiedScore = 0;

    @Column(name = "reliability_score")
    private Double reliabilityScore;

    @Column(name = "leadership_score")
    private Double leadershipScore;

    @Column(name = "consistency_score")
    private Double consistencyScore;

    @Column(name = "review_quality_score")
    private Double reviewQualityScore;

    @Column(name = "last_generated_at")
    private Instant lastGeneratedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static PortfolioSummary blank(UUID userId) {
        PortfolioSummary summary = new PortfolioSummary();
        summary.userId = userId;
        return summary;
    }

    public void applyEntry(PortfolioEntry entry) {
        this.projectsCompleted++;
        if (entry.isWasLead()) {
            this.leadershipStints++;
        }
        this.totalVerifiedScore += entry.getContributionScore();
        this.lastGeneratedAt = entry.getGeneratedAt();
        this.updatedAt = Instant.now();
    }
}
