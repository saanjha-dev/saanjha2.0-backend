package com.saanjha.modules.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.portfolio.entity.PortfolioEntry;
import com.saanjha.modules.portfolio.entity.PortfolioGenerationState;
import com.saanjha.modules.portfolio.entity.PortfolioSummary;
import com.saanjha.modules.portfolio.entity.ProjectCompletionSignal;
import com.saanjha.modules.portfolio.repository.PortfolioEntryRepository;
import com.saanjha.modules.portfolio.repository.PortfolioGenerationStateRepository;
import com.saanjha.modules.portfolio.repository.PortfolioSummaryRepository;
import com.saanjha.modules.portfolio.repository.ProjectCompletionSignalRepository;
import com.saanjha.modules.project.service.ProjectSnapshotProvider;
import com.saanjha.modules.project.service.ProjectSnapshotProvider.ProjectSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Exercises the module's one genuinely subtle behavior: generating a
 * PortfolioEntry correctly regardless of which of the two independent
 * upstream signals (TeamArchivedEvent roster, ProjectCompletedEvent gate)
 * arrives first — see PortfolioGenerationState's Javadoc.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioGenerationServiceTest {

    @Mock private PortfolioGenerationStateRepository stateRepository;
    @Mock private ProjectCompletionSignalRepository completionSignalRepository;
    @Mock private PortfolioEntryRepository entryRepository;
    @Mock private PortfolioSummaryRepository summaryRepository;
    @Mock private PortfolioTimelineService timelineService;
    @Mock private PortfolioBadgeEngine badgeEngine;
    @Mock private ProjectSnapshotProvider projectSnapshotProvider;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PortfolioGenerationService service;

    private UUID projectId;
    private UUID userId;
    private final Map<String, PortfolioGenerationState> stateStore = new HashMap<>();
    private final Map<UUID, ProjectCompletionSignal> signalStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new PortfolioGenerationService(stateRepository, completionSignalRepository, entryRepository,
                summaryRepository, timelineService, badgeEngine, projectSnapshotProvider, eventPublisher, new ObjectMapper());

        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        stateStore.clear();
        signalStore.clear();

        lenient().when(stateRepository.findWithLockByProjectIdAndUserId(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(stateStore.get(key(inv.getArgument(0), inv.getArgument(1)))));
        lenient().when(stateRepository.save(any())).thenAnswer(inv -> {
            PortfolioGenerationState state = inv.getArgument(0);
            stateStore.put(key(state.getProjectId(), state.getUserId()), state);
            return state;
        });
        lenient().when(stateRepository.findByProjectIdAndGeneratedFalse(any())).thenAnswer(inv ->
                stateStore.values().stream().filter(s -> s.getProjectId().equals(inv.getArgument(0)) && !s.isGenerated()).toList());
        lenient().when(stateRepository.findByProjectId(any())).thenAnswer(inv ->
                stateStore.values().stream().filter(s -> s.getProjectId().equals(inv.getArgument(0))).toList());
        lenient().doAnswer(inv -> {
            PortfolioGenerationState state = inv.getArgument(0);
            stateStore.remove(key(state.getProjectId(), state.getUserId()));
            return null;
        }).when(stateRepository).delete(any());

        lenient().when(completionSignalRepository.findByProjectId(any())).thenAnswer(inv -> Optional.ofNullable(signalStore.get(inv.getArgument(0))));
        lenient().when(completionSignalRepository.save(any())).thenAnswer(inv -> {
            ProjectCompletionSignal signal = inv.getArgument(0);
            signalStore.put(signal.getProjectId(), signal);
            return signal;
        });

        lenient().when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(entryRepository.existsByUserIdAndProjectId(any(), any())).thenReturn(false);
        lenient().when(entryRepository.existsByUserIdAndWasLeadTrue(any())).thenReturn(false);
        lenient().when(entryRepository.findByUserIdOrderByCompletedAtDesc(any(UUID.class))).thenReturn(List.of());

        lenient().when(summaryRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(projectSnapshotProvider.getSnapshot(any())).thenReturn(
                Optional.of(new ProjectSnapshot(projectId, "Saanjha Backend", "saanjha-backend", "WEB", "A cool project", List.of("java", "spring-boot"))));
    }

    private String key(UUID projectId, UUID userId) {
        return projectId + ":" + userId;
    }

    @Test
    void teamArrivesFirst_thenProjectCompletes_generatesEntry() {
        Instant now = Instant.now();
        service.accumulateContribution(projectId, userId, 42.0, "TASK_COMPLETION");
        service.applyTeamRoster(projectId, List.of(new PortfolioGenerationService.RosterMember(userId, "LEAD", "Built the API", now.minusSeconds(1000), now, 10L)));

        verify(entryRepository, never()).save(any()); // Not yet — project hasn't completed.

        service.applyProjectCompletion(projectId, userId, now);

        verify(entryRepository).save(argThat((PortfolioEntry e) -> e.getUserId().equals(userId) && e.isWasLead() && e.getContributionScore() == 42.0));
        assertThat(stateStore.get(key(projectId, userId)).isGenerated()).isTrue();
    }

    @Test
    void projectCompletesFirst_thenTeamArrives_generatesEntry() {
        Instant now = Instant.now();
        service.accumulateContribution(projectId, userId, 15.0, "TASK_COMPLETION");
        service.applyProjectCompletion(projectId, userId, now);

        verify(entryRepository, never()).save(any()); // Not yet — no roster/role data.

        service.applyTeamRoster(projectId, List.of(new PortfolioGenerationService.RosterMember(userId, "MEMBER", null, now.minusSeconds(500), now, 5L)));

        verify(entryRepository).save(argThat((PortfolioEntry e) -> e.getUserId().equals(userId) && !e.isWasLead()));
    }

    @Test
    void redeliveredEventsAfterGeneration_neverCreateADuplicateEntry() {
        Instant now = Instant.now();
        service.applyTeamRoster(projectId, List.of(new PortfolioGenerationService.RosterMember(userId, "LEAD", null, now, now, 1L)));
        service.applyProjectCompletion(projectId, userId, now);
        verify(entryRepository, times(1)).save(any());

        // Redelivery of both signals — must not create a second entry.
        service.applyTeamRoster(projectId, List.of(new PortfolioGenerationService.RosterMember(userId, "LEAD", null, now, now, 1L)));
        service.applyProjectCompletion(projectId, userId, now);

        verify(entryRepository, times(1)).save(any());
    }

    @Test
    void contributionAfterGeneration_doesNotReopenFrozenState() {
        Instant now = Instant.now();
        service.applyTeamRoster(projectId, List.of(new PortfolioGenerationService.RosterMember(userId, "MEMBER", null, now, now, 1L)));
        service.applyProjectCompletion(projectId, userId, now);
        assertThat(stateStore.get(key(projectId, userId)).isGenerated()).isTrue();

        service.accumulateContribution(projectId, userId, 999.0, "TASK_COMPLETION");

        assertThat(stateStore.get(key(projectId, userId)).getRunningScore()).isZero(); // Untouched — generation is final.
    }

    @Test
    void projectArchived_discardsPendingStateWithoutGeneratingAnEntry() {
        Instant now = Instant.now();
        service.applyTeamRoster(projectId, List.of(new PortfolioGenerationService.RosterMember(userId, "MEMBER", null, now, now, 1L)));

        service.discardPending(projectId);

        verify(stateRepository).delete(any());
        assertThat(stateStore.values().stream().anyMatch(s -> s.getProjectId().equals(projectId))).isFalse();

        // A late/incorrect ProjectCompletedEvent for an already-discarded project must not fabricate an entry.
        service.applyProjectCompletion(projectId, userId, now);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void missingProjectSnapshot_leavesStatePendingForRetryInsteadOfFailingSilently() {
        when(projectSnapshotProvider.getSnapshot(any())).thenReturn(Optional.empty());
        Instant now = Instant.now();

        service.applyTeamRoster(projectId, List.of(new PortfolioGenerationService.RosterMember(userId, "MEMBER", null, now, now, 1L)));
        service.applyProjectCompletion(projectId, userId, now);

        verify(entryRepository, never()).save(any());
        assertThat(stateStore.get(key(projectId, userId)).isGenerated()).isFalse();
    }
}
