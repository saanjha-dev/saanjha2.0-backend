package com.saanjha.modules.contribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.contribution.entity.*;
import com.saanjha.modules.contribution.repository.*;
import com.saanjha.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

    @Mock private ContributionLedgerRepository ledgerRepository;
    @Mock private ContributionSummaryRepository summaryRepository;
    @Mock private ReputationProfileRepository reputationRepository;
    @Mock private ContributionSnapshotRepository snapshotRepository;
    @Mock private ScoringWeightsRepository scoringWeightsRepository;
    @Mock private TaskWatchRepository taskWatchRepository;
    @Mock private ProjectTeamSizeWatchRepository teamSizeWatchRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ContributionService contributionService;

    private UUID userId;
    private UUID projectId;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        contributionService = new ContributionService(ledgerRepository, summaryRepository, reputationRepository,
                snapshotRepository, scoringWeightsRepository, taskWatchRepository, teamSizeWatchRepository,
                eventPublisher, new ObjectMapper());
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        taskId = UUID.randomUUID();

        lenient().when(ledgerRepository.save(any(ContributionLedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(summaryRepository.save(any(ContributionSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(reputationRepository.save(any(ReputationProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(scoringWeightsRepository.findByContributionTypeAndActiveTrue(any()))
                .thenReturn(Optional.of(new ScoringWeights(1, ContributionType.TASK_COMPLETION, 10.0, "SYSTEM")));
        lenient().when(scoringWeightsRepository.findByActiveTrue()).thenReturn(List.of(new ScoringWeights(1, ContributionType.TASK_COMPLETION, 10.0, "SYSTEM")));
        lenient().when(taskWatchRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(taskWatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(teamSizeWatchRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(summaryRepository.findById(userId)).thenReturn(Optional.empty());
        lenient().when(reputationRepository.findById(userId)).thenReturn(Optional.empty());
    }

    @Test
    void recordTaskCompletion_happyPath_createsLedgerEntryAndUpdatesSummary() {
        when(ledgerRepository.findFirstBySourceReferenceIdAndSourceTypeAndIsReversalFalse(taskId, "TASK_COMPLETED"))
                .thenReturn(Optional.empty());

        contributionService.recordTaskCompletion(taskId, projectId, userId, UUID.randomUUID(),
                5, "HIGH", 10.0, 9.0, UUID.randomUUID(), "FEATURE", Instant.now());

        verify(ledgerRepository, atLeastOnce()).save(any(ContributionLedgerEntry.class));
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    @Test
    void recordTaskCompletion_withNoAssignee_isNoOp() {
        contributionService.recordTaskCompletion(taskId, projectId, null, UUID.randomUUID(),
                5, "HIGH", 10.0, 9.0, UUID.randomUUID(), "FEATURE", Instant.now());

        verifyNoInteractions(ledgerRepository);
    }

    @Test
    void recordTaskCompletion_selfReview_flagsTheEntry() {
        UUID assignee = userId;
        when(ledgerRepository.findFirstBySourceReferenceIdAndSourceTypeAndIsReversalFalse(taskId, "TASK_COMPLETED"))
                .thenReturn(Optional.empty());

        contributionService.recordTaskCompletion(taskId, projectId, assignee, UUID.randomUUID(),
                null, null, null, 1.0, assignee, "BUG", Instant.now()); // reviewedBy == assignee

        verify(ledgerRepository).save(argThat(entry ->
                entry.getIntegrityFlag() == IntegrityFlag.SELF_REVIEW));
    }

    @Test
    void recordTaskCompletion_reCompletionAfterReopen_issuesReversalThenNewEntry() {
        ContributionLedgerEntry existing = ContributionLedgerEntry.create(
                userId, projectId, "TASK_COMPLETED", taskId, ContributionType.TASK_COMPLETION, "FEATURE",
                10.0, 1.0, 1.0, 1.0, "[]", IntegrityFlag.NONE, 1, Instant.now().minusSeconds(3600));

        when(ledgerRepository.findFirstBySourceReferenceIdAndSourceTypeAndIsReversalFalse(taskId, "TASK_COMPLETED"))
                .thenReturn(Optional.of(existing));

        contributionService.recordTaskCompletion(taskId, projectId, userId, UUID.randomUUID(),
                5, "HIGH", 10.0, 15.0, UUID.randomUUID(), "FEATURE", Instant.now());

        // One save for the reversal, one for the fresh scored entry.
        verify(ledgerRepository, times(2)).save(any(ContributionLedgerEntry.class));
    }

    @Test
    void issueCorrection_onReversalEntry_isRejected() {
        ContributionLedgerEntry reversal = ContributionLedgerEntry.reversalOf(
                ContributionLedgerEntry.create(userId, projectId, "TASK_COMPLETED", taskId, ContributionType.TASK_COMPLETION,
                        null, 10.0, 1.0, 1.0, 1.0, "[]", IntegrityFlag.NONE, 1, Instant.now()),
                "test");
        when(ledgerRepository.findById(any())).thenReturn(Optional.of(reversal));

        assertThatThrownBy(() -> contributionService.issueCorrection(UUID.randomUUID(), UUID.randomUUID(), "oops"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void issueCorrection_onOriginalEntry_publishesCorrectedEvent() {
        ContributionLedgerEntry original = ContributionLedgerEntry.create(
                userId, projectId, "TASK_COMPLETED", taskId, ContributionType.TASK_COMPLETION, null,
                10.0, 1.0, 1.0, 1.0, "[]", IntegrityFlag.NONE, 1, Instant.now());
        when(ledgerRepository.findById(any())).thenReturn(Optional.of(original));

        contributionService.issueCorrection(UUID.randomUUID(), UUID.randomUUID(), "Awarded in error");

        verify(eventPublisher).publishEvent(argThat(e -> e.getClass().getSimpleName().equals("ContributionCorrectedEvent")));
    }
}
