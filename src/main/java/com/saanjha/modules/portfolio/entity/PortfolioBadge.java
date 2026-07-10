package com.saanjha.modules.portfolio.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only, awarded exactly once per (user, badgeType) — enforced at the
 * DB level (see V17 migration's unique constraint), not just in application
 * code, so a concurrent double-award from a redelivered event cannot slip
 * through. No revocation path exists in v1: a later contribution correction
 * dropping someone below a threshold does not retract an already-awarded
 * badge — documented as a known tradeoff, same spirit as
 * {@code PortfolioSummary} not rewinding on corrections.
 */
@Entity
@Table(name = "ptf_badges", schema = "ptf", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "badge_type"})
})
public class PortfolioBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false, length = 40)
    private BadgeType badgeType;

    /** Small structured context for why this was awarded, e.g. {"projectId":"..."} or {"milestoneValue":100}. Display-only, never re-derived from. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, columnDefinition = "jsonb")
    private String evidenceJson = "{}";

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();

    protected PortfolioBadge() {
        // JPA
    }

    public static PortfolioBadge create(UUID userId, BadgeType badgeType, String evidenceJson, Instant awardedAt) {
        PortfolioBadge badge = new PortfolioBadge();
        badge.userId = userId;
        badge.badgeType = badgeType;
        badge.evidenceJson = evidenceJson;
        badge.awardedAt = awardedAt;
        return badge;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public BadgeType getBadgeType() {
        return badgeType;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public Instant getAwardedAt() {
        return awardedAt;
    }
}
