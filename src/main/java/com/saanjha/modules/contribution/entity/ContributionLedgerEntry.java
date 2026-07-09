package com.saanjha.modules.contribution.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The fundamental unit of trust in this module: one immutable fact,
 * "this user did this thing, worth this much, for these explainable
 * reasons." There is no {@code @Version} column and no update path for the
 * scoring fields — this entity has no setters for anything but the fields a
 * correction legitimately appends (see {@code correctionOfEntryId}).
 * Corrections are new rows referencing the original, never edits to it
 * (the Stripe-ledger pattern the brief explicitly points to).
 *
 * {@code explanationJson} is the Explanation Engine's structured breakdown —
 * an ordered list of named steps (e.g. "Base score: 10", "Complexity HIGH:
 * x1.5", "Reviewed successfully: x1.2"), never just the final number.
 */
@Entity
@Table(name = "con_ledger_entries", schema = "con")
public class ContributionLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_reference_id", nullable = false)
    private UUID sourceReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_type", nullable = false, length = 30)
    private ContributionType contributionType;

    @Column(name = "context_task_type", length = 20)
    private String contextTaskType;

    @Column(name = "base_score", nullable = false)
    private double baseScore;

    @Column(name = "complexity_multiplier", nullable = false)
    private double complexityMultiplier = 1.0;

    @Column(name = "quality_multiplier", nullable = false)
    private double qualityMultiplier = 1.0;

    @Column(name = "leadership_multiplier", nullable = false)
    private double leadershipMultiplier = 1.0;

    @Column(name = "final_score", nullable = false)
    private double finalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation", nullable = false, columnDefinition = "jsonb")
    private String explanationJson = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_flag", nullable = false, length = 30)
    private IntegrityFlag integrityFlag = IntegrityFlag.NONE;

    @Column(name = "correction_of_entry_id")
    private UUID correctionOfEntryId;

    @Column(name = "is_reversal", nullable = false)
    private boolean isReversal = false;

    @Column(name = "scoring_weights_version", nullable = false)
    private int scoringWeightsVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    protected ContributionLedgerEntry() {
        // JPA
    }

    public static ContributionLedgerEntry create(UUID userId, UUID projectId, String sourceType, UUID sourceReferenceId,
                                                  ContributionType contributionType, String contextTaskType,
                                                  double baseScore, double complexityMultiplier, double qualityMultiplier,
                                                  double leadershipMultiplier, String explanationJson,
                                                  IntegrityFlag integrityFlag, int scoringWeightsVersion, Instant occurredAt) {
        ContributionLedgerEntry entry = new ContributionLedgerEntry();
        entry.userId = userId;
        entry.projectId = projectId;
        entry.sourceType = sourceType;
        entry.sourceReferenceId = sourceReferenceId;
        entry.contributionType = contributionType;
        entry.contextTaskType = contextTaskType;
        entry.baseScore = baseScore;
        entry.complexityMultiplier = complexityMultiplier;
        entry.qualityMultiplier = qualityMultiplier;
        entry.leadershipMultiplier = leadershipMultiplier;
        entry.finalScore = baseScore * complexityMultiplier * qualityMultiplier * leadershipMultiplier;
        entry.explanationJson = explanationJson;
        entry.integrityFlag = integrityFlag;
        entry.scoringWeightsVersion = scoringWeightsVersion;
        entry.occurredAt = occurredAt;
        entry.recordedAt = Instant.now();
        return entry;
    }

    /** A reversal always exactly negates the original's final score, so summing the ledger for any user always nets out corrected entries correctly. */
    public static ContributionLedgerEntry reversalOf(ContributionLedgerEntry original, String reason) {
        ContributionLedgerEntry reversal = new ContributionLedgerEntry();
        reversal.userId = original.userId;
        reversal.projectId = original.projectId;
        reversal.sourceType = original.sourceType;
        reversal.sourceReferenceId = original.sourceReferenceId;
        reversal.contributionType = original.contributionType;
        reversal.contextTaskType = original.contextTaskType;
        reversal.baseScore = -original.baseScore;
        reversal.complexityMultiplier = original.complexityMultiplier;
        reversal.qualityMultiplier = original.qualityMultiplier;
        reversal.leadershipMultiplier = original.leadershipMultiplier;
        reversal.finalScore = -original.finalScore;
        reversal.explanationJson = "[{\"step\":\"Reversal\",\"detail\":\"" + reason.replace("\"", "'") + "\"}]";
        reversal.integrityFlag = IntegrityFlag.NONE;
        reversal.correctionOfEntryId = original.id;
        reversal.isReversal = true;
        reversal.scoringWeightsVersion = original.scoringWeightsVersion;
        reversal.occurredAt = Instant.now();
        reversal.recordedAt = Instant.now();
        return reversal;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceReferenceId() {
        return sourceReferenceId;
    }

    public ContributionType getContributionType() {
        return contributionType;
    }

    public String getContextTaskType() {
        return contextTaskType;
    }

    public double getBaseScore() {
        return baseScore;
    }

    public double getComplexityMultiplier() {
        return complexityMultiplier;
    }

    public double getQualityMultiplier() {
        return qualityMultiplier;
    }

    public double getLeadershipMultiplier() {
        return leadershipMultiplier;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public String getExplanationJson() {
        return explanationJson;
    }

    public IntegrityFlag getIntegrityFlag() {
        return integrityFlag;
    }

    public UUID getCorrectionOfEntryId() {
        return correctionOfEntryId;
    }

    public boolean isReversal() {
        return isReversal;
    }

    public int getScoringWeightsVersion() {
        return scoringWeightsVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
