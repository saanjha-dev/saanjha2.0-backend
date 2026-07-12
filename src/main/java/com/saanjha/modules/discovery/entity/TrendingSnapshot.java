package com.saanjha.modules.discovery.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A ranked, point-in-time snapshot produced by the Trending Engine's
 * scheduled recompute job — never computed at request time (Section
 * "Trending Engine" of the brief). Each recompute run replaces the previous
 * batch for its (entityType, window) pair in the same transaction it inserts
 * the new one.
 */
@Entity
@Table(name = "dsc_trending_snapshots", schema = "dsc")
@Getter
@Setter
public class TrendingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private TrendingEntityType entityType;

    @Column(name = "entity_key", nullable = false, length = 255)
    private String entityKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_type", nullable = false, length = 10)
    private TrendingWindow window;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private int rank;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();
}
