package com.saanjha.modules.contribution.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Reputation is deliberately its own entity, not columns bolted onto
 * {@link ContributionSummary} — the brief is explicit: "Never mix
 * reputation with raw contribution." A prolific contributor with a poor
 * completion rate should show high contribution volume AND low reliability
 * reputation simultaneously; conflating them into one score would hide
 * exactly the signal a recruiter or Lead most needs.
 *
 * {@code communicationScore} and {@code mentorshipScore} are left
 * permanently NULL until this platform has a real data source for them
 * (Chat doesn't exist yet; there's no mentorship-tracking concept anywhere
 * in the system). Returning 0 would claim "this person has zero
 * communication skill," which is a false, harmful claim to make from an
 * absence of data — NULL honestly means "unmeasured," not "bad."
 */
@Entity
@Table(name = "con_reputation_profiles", schema = "con")
@Getter
@Setter
public class ReputationProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "reliability_score")
    private Double reliabilityScore;

    @Column(name = "leadership_score")
    private Double leadershipScore;

    @Column(name = "consistency_score")
    private Double consistencyScore;

    @Column(name = "review_quality_score")
    private Double reviewQualityScore;

    @Column(name = "communication_score")
    private Double communicationScore; // Reserved — no data source until Chat exists.

    @Column(name = "mentorship_score")
    private Double mentorshipScore; // Reserved — no data source until mentorship tracking exists.

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static ReputationProfile blank(UUID userId) {
        ReputationProfile profile = new ReputationProfile();
        profile.userId = userId;
        return profile;
    }
}
