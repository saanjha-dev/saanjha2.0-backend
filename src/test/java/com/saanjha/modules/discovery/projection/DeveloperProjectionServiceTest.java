package com.saanjha.modules.discovery.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.contribution.event.ContributionEvents.ContributionRecordedEvent;
import com.saanjha.modules.contribution.event.ContributionEvents.ReputationUpdatedEvent;
import com.saanjha.modules.discovery.entity.DeveloperSearchDocument;
import com.saanjha.modules.discovery.repository.DeveloperSearchDocumentRepository;
import com.saanjha.modules.discovery.search.SuggestionService;
import com.saanjha.modules.user.event.UserEvents.SkillSignal;
import com.saanjha.modules.user.event.UserEvents.UserDiscoveryUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeveloperProjectionServiceTest {

    @Mock private DeveloperSearchDocumentRepository repository;
    @Mock private SuggestionService suggestionService;

    private DeveloperProjectionService service;

    @BeforeEach
    void setUp() {
        service = new DeveloperProjectionService(repository, new ObjectMapper(), suggestionService);
    }

    @Test
    void discoverySync_createsDocumentAndRecordsSuggestions() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        UserDiscoveryUpdatedEvent event = new UserDiscoveryUpdatedEvent(
                userId, "Asha Verma", "asha_codes", "Backend Engineer", "bio", "Pune", "SENIOR",
                List.of(new SkillSignal("Java", "ADVANCED", true)), List.of("OSS"), 90, 3, false, Instant.now());

        service.applyDiscoverySync(event);

        verify(repository).save(any(DeveloperSearchDocument.class));
        verify(suggestionService).recordTerm(eq("asha_codes"), any());
        verify(suggestionService).recordTerm(eq("Java"), any());
    }

    @Test
    void discoverySync_forDeletedProfile_doesNotRecordSuggestions() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        UserDiscoveryUpdatedEvent event = new UserDiscoveryUpdatedEvent(
                userId, "Deleted User", "deleted_user", null, null, null, null,
                List.of(), List.of(), 0, 0, true, Instant.now());

        service.applyDiscoverySync(event);

        verifyNoInteractions(suggestionService);
    }

    @Test
    void contributionRecorded_accumulatesRunningTotal() {
        UUID userId = UUID.randomUUID();
        DeveloperSearchDocument existing = new DeveloperSearchDocument();
        existing.setUserId(userId);
        existing.setContributionTotalScore(50.0);
        when(repository.findById(userId)).thenReturn(Optional.of(existing));

        service.applyContributionRecorded(new ContributionRecordedEvent(
                UUID.randomUUID(), userId, UUID.randomUUID(), "TASK_COMPLETION", "IMPLEMENTATION",
                25.0, false, 2, "CLEAN", Instant.now()));

        assertThat(existing.getContributionTotalScore()).isEqualTo(75.0);
    }

    @Test
    void reputationUpdated_missingDocument_isSkippedSafely() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        service.applyReputationUpdated(new ReputationUpdatedEvent(userId, 80.0, 70.0, 60.0, 90.0, Instant.now()));

        verify(repository, never()).save(any());
    }
}
