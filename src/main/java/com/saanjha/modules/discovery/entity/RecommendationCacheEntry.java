package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The Recommendation Engine's output cache — request-time reads never
 * recompute from scratch. {@code payload} is a JSON array of
 * {@code RecommendationResult.Item} (see recommendation package), stored as
 * a raw JSON string per this codebase's established JSONB convention.
 */
@Entity
@Table(name = "dsc_recommendation_cache", schema = "dsc", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "recommendation_type"})
})
@Getter
@Setter
public class RecommendationCacheEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 30)
    private RecommendationType recommendationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "[]";

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
