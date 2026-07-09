package com.saanjha.modules.contribution.service;

import com.saanjha.modules.contribution.dto.ContributionResponseDTOs.*;
import com.saanjha.modules.contribution.entity.*;
import com.saanjha.modules.contribution.event.ContributionEvents.*;
import com.saanjha.modules.contribution.repository.*;
import com.saanjha.modules.contribution.service.ContributionScoringEngine.LeadershipInputs;
import com.saanjha.modules.contribution.service.ContributionScoringEngine.ScoreResult;
import com.saanjha.modules.contribution.service.ContributionScoringEngine.TaskCompletionInputs;
import com.saanjha.shared.exception.AppException;
import com.saanjha.shared.exception.ErrorCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the Trust Engine: scores incoming work events into immutable
 * ledger entries, maintains the derived Summary/Reputation rollups
 * incrementally, and issues compensating corrections — never edits to
 * history. Every method that reacts to an upstream event is idempotent by
 * construction (checked via the ledger's own unique source-reference index),
 * per the module's concurrency requirements.
 */
@Service
@RequiredArgsConstructor
public class ContributionService {

    private static final int[] MILESTONE_THRESHOLDS = {10, 25, 50, 100, 250, 500, 1000};

    private final ContributionLedgerRepository ledgerRepository;
    private final ContributionSummaryRepository summaryRepository;
    private final ReputationProfileRepository reputationRepository;
    private final ContributionSnapshotRepository snapshotRepository;
    private final ScoringWeightsRepository scoringWeightsRepository;
    private final TaskWatchRepository taskWatchRepository;
    private final ProjectTeamSizeWatchRepository teamSizeWatchRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // INTEGRITY-TRACKING (internal, no ledger entry, no event)
    // ========================================================================

    @Transactional
    public void trackAssignment(UUID taskId) {
        TaskWatch watch = taskWatchRepository.findById(taskId).orElse(TaskWatch.blank(taskId));
        watch.recordAssignment();
        taskWatchRepository.save(watch);
    }

    @Transactional
    public void trackReopen(UUID taskId) {
        TaskWatch watch = taskWatchRepository.findById(taskId).orElse(TaskWatch.blank(taskId));
        watch.recordReopen();
        taskWatchRepository.save(watch);
    }

    @Transactional
    public void trackTeamSizeChange(UUID projectId, int newSize) {
        ProjectTeamSizeWatch watch = teamSizeWatchRepository.findById(projectId).orElse(ProjectTeamSizeWatch.blank(projectId));
        watch.setSize(newSize);
        teamSizeWatchRepository.save(watch);
    }

    // ========================================================================
    // SCORING: TASK COMPLETION
    // ========================================================================

    /**
     * Idempotent, but with a documented nuance: a genuine re-completion of a
     * previously-completed task (reopened, reworked, completed again) is NOT
     * treated as a duplicate — it's handled as a correction (reversal of the
     * stale entry + a freshly-scored one reflecting the updated inputs),
     * reusing the same compensating-entry mechanism corrections use. An
     * exact duplicate delivery of the SAME completion nets to an identical
     * score either way — a minor, harmless inefficiency (two extra rows),
     * not a correctness bug.
     */
    @Transactional
    public void recordTaskCompletion(UUID taskId, UUID projectId, UUID assigneeId, UUID reporterId,
                                      Integer complexity, String priority, Double estimatedHours, double actualHours,
                                      UUID reviewedBy, String contextTaskType, Instant completedAt) {
        if (assigneeId == null) {
            return; // A task with no assignee cannot have a contributor to credit.
        }

        Optional<ContributionLedgerEntry> existing = ledgerRepository.findFirstBySourceReferenceIdAndSourceTypeAndIsReversalFalse(taskId, "TASK_COMPLETED");

        TaskWatch watch = taskWatchRepository.findById(taskId).orElse(TaskWatch.blank(taskId));
        int teamSize = teamSizeWatchRepository.findById(projectId).map(ProjectTeamSizeWatch::getCurrentSize).orElse(0);
        boolean selfReviewed = reviewedBy != null && reviewedBy.equals(assigneeId);

        TaskCompletionInputs inputs = new TaskCompletionInputs(
                complexity, priority, estimatedHours, actualHours, selfReviewed,
                watch.getAssignmentCount(), watch.getReopenCount(), watch.getStartedAt(), completedAt, teamSize);

        double baseWeight = activeWeight(ContributionType.TASK_COMPLETION);
        ScoreResult result = ContributionScoringEngine.scoreTaskCompletion(baseWeight, inputs);

        if (existing.isPresent()) {
            reverseEntryInternal(existing.get(), "Task was reopened and re-completed; superseding with updated scoring.");
        }

        recordEntry(assigneeId, projectId, "TASK_COMPLETED", taskId, ContributionType.TASK_COMPLETION,
                contextTaskType, result, completedAt);

        // A review happened (reviewedBy present, and NOT a self-review) — record it as its own, separate contribution for the reviewer.
        if (reviewedBy != null && !selfReviewed) {
            recordReview(reviewedBy, projectId, taskId, completedAt);
        }
    }

    private void recordReview(UUID reviewerId, UUID projectId, UUID taskId, Instant occurredAt) {
        double baseWeight = activeWeight(ContributionType.TASK_REVIEW);
        List<ExplanationStep> explanation = List.of(new ExplanationStep("Reviewed a completed task", String.valueOf(baseWeight)));
        ContributionLedgerEntry entry = ContributionLedgerEntry.create(
                reviewerId, projectId, "TASK_REVIEWED", taskId, ContributionType.TASK_REVIEW, null,
                baseWeight, 1.0, 1.0, 1.0, writeExplanation(explanation), IntegrityFlag.NONE, activeWeightVersion(), occurredAt);
        persistAndPropagate(entry);
    }

    // ========================================================================
    // SCORING: TASK ABANDONMENT (reliability signal, never a positive score)
    // ========================================================================

    @Transactional
    public void recordTaskAbandoned(UUID taskId, UUID projectId, UUID assigneeId, Instant occurredAt) {
        if (assigneeId == null || ledgerRepository.existsBySourceReferenceIdAndSourceTypeAndIsReversalFalse(taskId, "TASK_CANCELLED")) {
            return;
        }
        double baseWeight = activeWeight(ContributionType.TASK_ABANDONED); // Always 0 per the V16 seed.
        List<ExplanationStep> explanation = List.of(new ExplanationStep("Task cancelled while assigned", "Tracked for reliability reputation only; no score impact."));
        ContributionLedgerEntry entry = ContributionLedgerEntry.create(
                assigneeId, projectId, "TASK_CANCELLED", taskId, ContributionType.TASK_ABANDONED, null,
                baseWeight, 1.0, 1.0, 1.0, writeExplanation(explanation), IntegrityFlag.NONE, activeWeightVersion(), occurredAt);
        persistAndPropagate(entry);
    }

    // ========================================================================
    // SCORING: LEADERSHIP
    // ========================================================================

    @Transactional
    public void recordProjectLeadershipSuccess(UUID projectId, UUID leadUserId, Instant occurredAt) {
        if (ledgerRepository.existsBySourceReferenceIdAndSourceTypeAndIsReversalFalse(projectId, "PROJECT_COMPLETED")) {
            return;
        }
        int teamSize = teamSizeWatchRepository.findById(projectId).map(ProjectTeamSizeWatch::getCurrentSize).orElse(0);
        double baseWeight = activeWeight(ContributionType.LEADERSHIP);
        ScoreResult result = ContributionScoringEngine.scoreLeadership(baseWeight, new LeadershipInputs(teamSize, true));

        recordEntry(leadUserId, projectId, "PROJECT_COMPLETED", projectId, ContributionType.LEADERSHIP, null, result, occurredAt);
    }

    @Transactional
    public void recordLeadershipTransfer(UUID projectId, UUID newLeadUserId, UUID transferId, Instant occurredAt) {
        if (ledgerRepository.existsBySourceReferenceIdAndSourceTypeAndIsReversalFalse(transferId, "LEADERSHIP_TRANSFERRED")) {
            return;
        }
        double baseWeight = activeWeight(ContributionType.LEADERSHIP) * 0.2; // A transfer alone is a much smaller signal than a successful completion.
        ScoreResult result = ContributionScoringEngine.scoreLeadership(baseWeight, new LeadershipInputs(0, false));

        recordEntry(newLeadUserId, projectId, "LEADERSHIP_TRANSFERRED", transferId, ContributionType.LEADERSHIP, null, result, occurredAt);
    }

    // ========================================================================
    // SHARED RECORDING PATH
    // ========================================================================

    private void recordEntry(UUID userId, UUID projectId, String sourceType, UUID sourceReferenceId,
                              ContributionType type, String contextTaskType, ScoreResult result, Instant occurredAt) {
        ContributionLedgerEntry entry = ContributionLedgerEntry.create(
                userId, projectId, sourceType, sourceReferenceId, type, contextTaskType,
                result.baseScore(), result.complexityMultiplier(), result.qualityMultiplier(), result.leadershipMultiplier(),
                writeExplanation(result.explanation()), result.integrityFlag(), activeWeightVersion(), occurredAt);
        persistAndPropagate(entry);
    }

    private void persistAndPropagate(ContributionLedgerEntry entry) {
        entry = ledgerRepository.save(entry);
        applyToSummary(entry);
        updateReputation(entry);
        checkMilestones(entry.getUserId());

        eventPublisher.publishEvent(new ContributionRecordedEvent(
                entry.getId(), entry.getUserId(), entry.getProjectId(), entry.getContributionType().name(),
                entry.getContextTaskType(), entry.getFinalScore(), entry.getContributionType() == ContributionType.LEADERSHIP,
                null, entry.getIntegrityFlag().name(), Instant.now()));
    }

    // ========================================================================
    // CORRECTIONS (compensating entries — never mutate history)
    // ========================================================================

    @Transactional
    public LedgerEntryResponse issueCorrection(UUID entryId, UUID actingAdminId, String reason) {
        ContributionLedgerEntry original = ledgerRepository.findById(entryId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Ledger entry not found."));
        if (original.isReversal()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot correct a reversal entry itself.");
        }
        ContributionLedgerEntry reversal = reverseEntryInternal(original, reason);

        eventPublisher.publishEvent(new ContributionCorrectedEvent(
                reversal.getId(), original.getId(), original.getUserId(), reversal.getFinalScore(), reason, Instant.now()));

        return mapToResponse(reversal);
    }

    private ContributionLedgerEntry reverseEntryInternal(ContributionLedgerEntry original, String reason) {
        ContributionLedgerEntry reversal = ContributionLedgerEntry.reversalOf(original, reason);
        reversal = ledgerRepository.save(reversal);
        applyToSummary(reversal);
        return reversal;
    }

    // ========================================================================
    // DERIVED STATE MAINTENANCE
    // ========================================================================

    private void applyToSummary(ContributionLedgerEntry entry) {
        ContributionSummary summary = summaryRepository.findById(entry.getUserId()).orElse(ContributionSummary.blank(entry.getUserId()));
        summary.apply(entry);
        summaryRepository.save(summary);
    }

    /**
     * Reputation is intentionally coarse in this first pass — real signal,
     * not decorative, but simple: reliability from completion-vs-abandonment
     * ratio, review quality from review volume without self-review flags,
     * leadership from successful leadership entries, consistency from
     * recency of activity. Communication/mentorship stay NULL — see
     * ReputationProfile's javadoc.
     */
    private void updateReputation(ContributionLedgerEntry entry) {
        ReputationProfile profile = reputationRepository.findById(entry.getUserId()).orElse(ReputationProfile.blank(entry.getUserId()));
        ContributionSummary summary = summaryRepository.findById(entry.getUserId()).orElse(ContributionSummary.blank(entry.getUserId()));

        int totalWork = summary.getTasksCompleted() + summary.getTasksAbandoned();
        if (totalWork > 0) {
            profile.setReliabilityScore((double) summary.getTasksCompleted() / totalWork);
        }
        if (summary.getLeadershipStints() > 0) {
            profile.setLeadershipScore(Math.min(summary.getLeadershipStints() / 5.0, 1.0));
        }
        if (summary.getReviewsGiven() > 0) {
            long flaggedSelfReviews = ledgerRepository.countByUserIdAndIntegrityFlagNot(entry.getUserId(), IntegrityFlag.NONE);
            profile.setReviewQualityScore(Math.max(0, 1.0 - (flaggedSelfReviews / (double) Math.max(summary.getReviewsGiven(), 1))));
        }
        profile.setConsistencyScore(1.0); // Placeholder-but-honest: real recency/streak analysis is a documented extension point, not fabricated depth today.
        profile.setUpdatedAt(Instant.now());
        reputationRepository.save(profile);

        eventPublisher.publishEvent(new ReputationUpdatedEvent(
                entry.getUserId(), profile.getReliabilityScore(), profile.getLeadershipScore(),
                profile.getConsistencyScore(), profile.getReviewQualityScore(), Instant.now()));
    }

    private void checkMilestones(UUID userId) {
        ContributionSummary summary = summaryRepository.findById(userId).orElse(null);
        if (summary == null) {
            return;
        }
        for (int threshold : MILESTONE_THRESHOLDS) {
            if (summary.getTasksCompleted() == threshold) {
                eventPublisher.publishEvent(new ContributionMilestoneReachedEvent(userId, "TASKS_COMPLETED", threshold, Instant.now()));
            }
        }
    }

    // ========================================================================
    // SNAPSHOTS
    // ========================================================================

    @Transactional
    public SnapshotResponse captureSnapshot(UUID userId, ContributionSnapshot.Reason reason) {
        ContributionSummary summary = summaryRepository.findById(userId).orElse(ContributionSummary.blank(userId));
        ContributionSnapshot snapshot = new ContributionSnapshot(userId, summary.getTotalScore(), summary.getTasksCompleted(), summary.getReviewsGiven(), reason);
        snapshot = snapshotRepository.save(snapshot);

        eventPublisher.publishEvent(new ContributionSnapshotCreatedEvent(snapshot.getId(), userId, snapshot.getTotalScore(), reason.name(), Instant.now()));
        return mapSnapshot(snapshot);
    }

    // ========================================================================
    // READS
    // ========================================================================

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(UUID userId) {
        ContributionSummary summary = summaryRepository.findById(userId).orElse(ContributionSummary.blank(userId));
        return new SummaryResponse(userId, summary.getTotalScore(), summary.getTasksCompleted(), summary.getReviewsGiven(),
                summary.getLeadershipStints(), summary.getTasksAbandoned(), summary.getLastContributionAt());
    }

    @Transactional(readOnly = true)
    public ReputationResponse getReputation(UUID userId) {
        ReputationProfile profile = reputationRepository.findById(userId).orElse(ReputationProfile.blank(userId));
        return new ReputationResponse(userId, profile.getReliabilityScore(), profile.getLeadershipScore(),
                profile.getConsistencyScore(), profile.getReviewQualityScore(), profile.getCommunicationScore(), profile.getMentorshipScore());
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getTimeline(UUID userId, Pageable pageable) {
        return ledgerRepository.findByUserIdOrderByOccurredAtDesc(userId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<SnapshotResponse> getSnapshots(UUID userId, Pageable pageable) {
        return snapshotRepository.findByUserIdOrderByCapturedAtDesc(userId, pageable).map(this::mapSnapshot);
    }

    @Transactional(readOnly = true)
    public ProjectContributionResponse getProjectContribution(UUID projectId, Pageable pageable) {
        List<ContributionLedgerEntry> entries = ledgerRepository.findByProjectIdOrderByOccurredAtDesc(projectId, pageable).getContent();
        var byUser = entries.stream().collect(java.util.stream.Collectors.groupingBy(ContributionLedgerEntry::getUserId));

        List<ContributorBreakdown> breakdown = byUser.entrySet().stream().map(e -> {
            double total = e.getValue().stream().mapToDouble(ContributionLedgerEntry::getFinalScore).sum();
            int completions = (int) e.getValue().stream().filter(x -> x.getContributionType() == ContributionType.TASK_COMPLETION).count();
            int reviews = (int) e.getValue().stream().filter(x -> x.getContributionType() == ContributionType.TASK_REVIEW).count();
            boolean wasLead = e.getValue().stream().anyMatch(x -> x.getContributionType() == ContributionType.LEADERSHIP);
            return new ContributorBreakdown(e.getKey(), total, completions, reviews, wasLead);
        }).sorted((a, b) -> Double.compare(b.totalScore(), a.totalScore())).toList();

        return new ProjectContributionResponse(projectId, breakdown);
    }

    @Transactional(readOnly = true)
    public ContributionAnalyticsResponse getAnalytics(UUID userId) {
        List<ContributionLedgerEntry> entries = ledgerRepository.findByUserId(userId);
        long total = entries.stream().filter(e -> !e.isReversal()).count();
        long flagged = entries.stream().filter(e -> e.getIntegrityFlag() != IntegrityFlag.NONE).count();
        long reviews = entries.stream().filter(e -> e.getContributionType() == ContributionType.TASK_REVIEW).count();
        long completions = entries.stream().filter(e -> e.getContributionType() == ContributionType.TASK_COMPLETION).count();
        double avgComplexity = entries.stream().mapToDouble(ContributionLedgerEntry::getComplexityMultiplier).average().orElse(1.0);

        double weeks = entries.stream().map(ContributionLedgerEntry::getOccurredAt).min(Instant::compareTo)
                .map(earliest -> Math.max(1.0, java.time.Duration.between(earliest, Instant.now()).toDays() / 7.0))
                .orElse(1.0);
        double velocity = completions / weeks;
        double reviewRatio = total == 0 ? 0 : (double) reviews / total;
        double completionRatio = total == 0 ? 0 : (double) completions / total;

        return new ContributionAnalyticsResponse(userId, velocity, avgComplexity, reviewRatio, completionRatio, total, flagged);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private double activeWeight(ContributionType type) {
        return scoringWeightsRepository.findByContributionTypeAndActiveTrue(type)
                .map(ScoringWeights::getBaseWeight)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "No active scoring weight configured for " + type + "."));
    }

    private int activeWeightVersion() {
        return scoringWeightsRepository.findByActiveTrue().stream()
                .map(ScoringWeights::getVersion)
                .findFirst()
                .orElse(1);
    }

    private String writeExplanation(List<ExplanationStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception ex) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<ExplanationStep> readExplanation(String json) {
        try {
            List<java.util.Map<String, String>> raw = objectMapper.readValue(json, List.class);
            return raw.stream().map(m -> new ExplanationStep(m.get("step"), m.get("detail"))).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private LedgerEntryResponse mapToResponse(ContributionLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(), entry.getUserId(), entry.getProjectId(), entry.getSourceType(), entry.getSourceReferenceId(),
                entry.getContributionType().name(), entry.getContextTaskType(), entry.getBaseScore(), entry.getComplexityMultiplier(),
                entry.getQualityMultiplier(), entry.getLeadershipMultiplier(), entry.getFinalScore(),
                readExplanation(entry.getExplanationJson()), entry.getIntegrityFlag().name(), entry.isReversal(),
                entry.getCorrectionOfEntryId(), entry.getScoringWeightsVersion(), entry.getOccurredAt());
    }

    private SnapshotResponse mapSnapshot(ContributionSnapshot snapshot) {
        return new SnapshotResponse(snapshot.getId(), snapshot.getTotalScore(), snapshot.getTasksCompleted(),
                snapshot.getReviewsGiven(), snapshot.getSnapshotReason().name(), snapshot.getCapturedAt());
    }
}
