package com.saanjha.modules.admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A per-user trust/risk cache, recalculated incrementally as reports are
 * upheld/dismissed and as {@code SuspiciousActivityDetectedEvent} arrives
 * from Auth (see AdminEventListener). Deliberately a simple weighted counter
 * today, not a model — the schema (a bounded numeric score + a small set of
 * countable inputs) is designed so a future ML-based risk model can replace
 * {@code TrustScoreService.recalculate}'s internals without a schema change:
 * this table is the "future AI hooks" surface named in the Admin brief's
 * Trust & Safety section, not the AI itself.
 */
@Entity
@Table(name = "adm_trust_scores", schema = "adm")
@Getter
@Setter
public class TrustScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /** 0-100, higher is more trusted. Starts at a neutral baseline, never shown raw to the subject. */
    @Column(nullable = false)
    private double score = 100.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private TrustRiskLevel riskLevel = TrustRiskLevel.LOW;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 0;

    @Column(name = "upheld_report_count", nullable = false)
    private int upheldReportCount = 0;

    @Column(name = "suspicious_activity_count", nullable = false)
    private int suspiciousActivityCount = 0;

    @Column(name = "last_recalculated_at")
    private Instant lastRecalculatedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
