package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-technology/skill rollup, incrementally maintained (same pattern as
 * Contribution's/Portfolio's own summary rollups) rather than recomputed at
 * request time. {@code technologyName} is normalized lowercase, matching
 * {@code UserProfileService.normalizeString}'s convention.
 */
@Entity
@Table(name = "dsc_technology_stats", schema = "dsc")
@Getter
@Setter
public class TechnologyStat {

    @Id
    @Column(name = "technology_name", length = 100)
    private String technologyName;

    @Column(name = "project_count", nullable = false)
    private int projectCount = 0;

    @Column(name = "developer_count", nullable = false)
    private int developerCount = 0;

    @Column(name = "verified_developer_count", nullable = false)
    private int verifiedDeveloperCount = 0;

    @Column(name = "trending_score", nullable = false)
    private double trendingScore = 0;

    @Column(name = "last_computed_at", nullable = false)
    private Instant lastComputedAt = Instant.now();
}
