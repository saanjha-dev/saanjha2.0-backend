package com.saanjha.modules.contribution.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by the Contribution module. This is THE contract
 * Portfolio, Discovery, and Notification must build against instead of
 * reading this module's schema — per the brief: "Portfolio must never
 * calculate contribution... Portfolio consumes Contribution events."
 *
 * {@link ContributionRecordedEvent} is deliberately the richest payload:
 * score, type, project impact, role, complexity, leadership flag, and the
 * full explanation steps — everything the brief's Portfolio Contract lists
 * ("Contribution Score, Contribution Type, Project Impact, Role, Complexity,
 * Leadership, Reputation, Timeline") arrives in this one event, so Portfolio
 * never needs a second lookup.
 */
public final class ContributionEvents {

    private ContributionEvents() {
    }

    public record ContributionRecordedEvent(
            UUID entryId, UUID userId, UUID projectId, String contributionType, String contextTaskType,
            double finalScore, boolean isLeadershipContribution, Integer complexity, String integrityFlag,
            Instant occurredAt
    ) {}

    /** Consumers: Portfolio (must reverse the same amount it previously added), Notification (rare, usually silent). */
    public record ContributionCorrectedEvent(
            UUID reversalEntryId, UUID originalEntryId, UUID userId, double scoreDelta, String reason, Instant occurredAt
    ) {}

    /** Fired when a user's tasksCompleted crosses a round-number threshold (10/25/50/100/...). Consumers: Notification. */
    public record ContributionMilestoneReachedEvent(
            UUID userId, String milestoneType, int milestoneValue, Instant occurredAt
    ) {}

    /** Consumers: Portfolio (reputation display), Discovery (future ranking signal — see the module's Discovery Contract notes). */
    public record ReputationUpdatedEvent(
            UUID userId, Double reliabilityScore, Double leadershipScore, Double consistencyScore, Double reviewQualityScore, Instant occurredAt
    ) {}

    /** Consumers: Portfolio (historical trend charts without re-summing the ledger). */
    public record ContributionSnapshotCreatedEvent(
            UUID snapshotId, UUID userId, double totalScore, String reason, Instant occurredAt
    ) {}
}
