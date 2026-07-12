package com.saanjha.modules.discovery.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.config.DiscoveryMetrics;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import com.saanjha.modules.discovery.search.SuggestionService;
import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectDiscoveryUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectProjectionServiceTest {

    @Mock private ProjectSearchDocumentRepository repository;
    @Mock private SuggestionService suggestionService;
    @Mock private DiscoveryMetrics metrics;

    private ProjectProjectionService service;

    @BeforeEach
    void setUp() {
        service = new ProjectProjectionService(repository, new ObjectMapper(), suggestionService, metrics);
    }

    @Test
    void discoverySync_forRecruitingProject_createsIndexedDocument() {
        UUID projectId = UUID.randomUUID();
        when(repository.findById(projectId)).thenReturn(Optional.empty());

        ProjectDiscoveryUpdatedEvent event = new ProjectDiscoveryUpdatedEvent(
                projectId, UUID.randomUUID(), "My Project", "my-project", "desc", "WEB", "PUBLIC", "RECRUITING",
                List.of("Java"), List.of("backend"), 5, 1, Instant.now());

        service.applyDiscoverySync(event);

        ArgumentCaptor<ProjectSearchDocument> captor = ArgumentCaptor.forClass(ProjectSearchDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isIndexed()).isTrue();
        assertThat(captor.getValue().getTitle()).isEqualTo("My Project");
        verify(suggestionService).recordTerm(eq("My Project"), any());
    }

    @Test
    void discoverySync_forDraftProject_isNotIndexed() {
        UUID projectId = UUID.randomUUID();
        when(repository.findById(projectId)).thenReturn(Optional.empty());

        ProjectDiscoveryUpdatedEvent event = new ProjectDiscoveryUpdatedEvent(
                projectId, UUID.randomUUID(), "Draft Project", "draft-project", "desc", "WEB", "PUBLIC", "DRAFT",
                List.of(), List.of(), 5, 0, Instant.now());

        service.applyDiscoverySync(event);

        ArgumentCaptor<ProjectSearchDocument> captor = ArgumentCaptor.forClass(ProjectSearchDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isIndexed()).isFalse();
    }

    @Test
    void archived_marksExistingDocumentUnindexed() {
        UUID projectId = UUID.randomUUID();
        ProjectSearchDocument existing = new ProjectSearchDocument();
        existing.setProjectId(projectId);
        existing.setIndexed(true);
        when(repository.findById(projectId)).thenReturn(Optional.of(existing));

        service.applyArchived(new ProjectArchivedEvent(projectId, "RECRUITING", UUID.randomUUID(), "abandoned", Instant.now()));

        assertThat(existing.isIndexed()).isFalse();
        assertThat(existing.getStatus()).isEqualTo("ARCHIVED");
        verify(repository).save(existing);
    }

    @Test
    void archived_missingDocument_doesNothing() {
        UUID projectId = UUID.randomUUID();
        when(repository.findById(projectId)).thenReturn(Optional.empty());

        service.applyArchived(new ProjectArchivedEvent(projectId, "RECRUITING", UUID.randomUUID(), "abandoned", Instant.now()));

        verify(repository, never()).save(any());
    }
}
