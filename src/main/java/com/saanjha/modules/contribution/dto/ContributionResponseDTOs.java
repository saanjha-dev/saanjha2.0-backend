package com.saanjha.modules.contribution.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ContributionResponseDTOs {

    /** One named step in the Explanation Engine's breakdown — never just a final number. */
    public record ExplanationStep(
            String step,
            String detail
    ) {}

    public record LedgerEntryResponse(
            UUID id,
            UUID userId,
            UUID projectId,
            String sourceType,
            UUID sourceReferenceId,
            String contributionType,
            String contextTaskType,
            double baseScore,
            double complexityMultiplier,
            double qualityMultiplier,
            double leadershipMultiplier,
            double finalScore,
            List<ExplanationStep> explanation,
            String integrityFlag,
            boolean isReversal,
            UUID correctionOfEntryId,
            int scoringWeightsVersion,
            Instant occurredAt
    ) {}

    public record SummaryResponse(
            UUID userId,
            double totalScore,
            int tasksCompleted,
            int reviewsGiven,
            int leadershipStints,
            int tasksAbandoned,
            Instant lastContributionAt
    ) {}

    /** Reputation is always returned separately from Summary — never merged into one payload — per the module's core design principle. */
    public record ReputationResponse(
            UUID userId,
            Double reliabilityScore,
            Double leadershipScore,
            Double consistencyScore,
            Double reviewQualityScore,
            Double communicationScore,
            Double mentorshipScore
    ) {}

    public record SnapshotResponse(
            UUID id,
            double totalScore,
            int tasksCompleted,
            int reviewsGiven,
            String snapshotReason,
            Instant capturedAt
    ) {}

    /** Project/Team Contribution read model — live-aggregated over the ledger, same design choice Task made for its own analytics. */
    public record ProjectContributionResponse(
            UUID projectId,
            List<ContributorBreakdown> contributors
    ) {}

    public record ContributorBreakdown(
            UUID userId,
            double totalScore,
            int taskCompletions,
            int reviews,
            boolean wasLead
    ) {}

    public record ContributionAnalyticsResponse(
            UUID userId,
            double velocityPerWeek,
            double averageComplexity,
            double reviewRatio,
            double completionRatio,
            long totalContributions,
            long flaggedContributions
    ) {}
}
