package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Discovery's read model of a developer, built from
 * {@code UserEvents.UserDiscoveryUpdatedEvent} and enriched incrementally by
 * {@code ContributionEvents} (reputation, running score) and
 * {@code PortfolioEvents} (badge count, visibility).
 *
 * {@code availabilityStatus}/{@code remotePreference} are deliberate
 * extension points (see V22 migration Javadoc and the architecture-review
 * discussion) — no upstream event populates them today. They must never be
 * defaulted to a non-null value or used as a hard filter until a real event
 * sets them; ranking/matching code must treat NULL as "unknown," not "no
 * preference."
 */
@Entity
@Table(name = "dsc_developer_documents", schema = "dsc")
@Getter
@Setter
public class DeveloperSearchDocument {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "unique_handle", length = 50)
    private String uniqueHandle;

    @Column(length = 255)
    private String headline;

    @Column(name = "bio_excerpt", columnDefinition = "TEXT")
    private String bioExcerpt;

    @Column(length = 150)
    private String location;

    @Column(name = "experience_level", length = 30)
    private String experienceLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String skills = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String interests = "[]";

    @Column(name = "profile_score", nullable = false)
    private int profileScore;

    @Column(name = "projects_completed", nullable = false)
    private int projectsCompleted;

    @Column(name = "reliability_score")
    private Double reliabilityScore;

    @Column(name = "leadership_score")
    private Double leadershipScore;

    @Column(name = "consistency_score")
    private Double consistencyScore;

    @Column(name = "review_quality_score")
    private Double reviewQualityScore;

    @Column(name = "contribution_total_score", nullable = false)
    private double contributionTotalScore = 0;

    @Column(name = "portfolio_badge_count", nullable = false)
    private int portfolioBadgeCount = 0;

    @Column(name = "portfolio_visibility", length = 20)
    private String portfolioVisibility;

    /** Extension point — see class Javadoc. Never populated today. */
    @Column(name = "availability_status", length = 30)
    private String availabilityStatus;

    /** Extension point — see class Javadoc. Never populated today. */
    @Column(name = "remote_preference", length = 30)
    private String remotePreference;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
