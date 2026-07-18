package com.saanjha.modules.discovery.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saanjha.modules.discovery.entity.ProjectSearchDocument;
import com.saanjha.modules.discovery.entity.SuggestionEntityType;
import com.saanjha.modules.discovery.repository.ProjectSearchDocumentRepository;
import com.saanjha.modules.discovery.search.SuggestionService;
import com.saanjha.modules.discovery.config.DiscoveryMetrics;
import com.saanjha.modules.project.event.ProjectEvents.ProjectArchivedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectCompletedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectDiscoveryUpdatedEvent;
import com.saanjha.modules.project.event.ProjectEvents.ProjectPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Add these two imports:
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Builds and maintains {@link ProjectSearchDocument} purely from Project's
 * own events — never reads {@code prj.prj_projects} directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectProjectionService {

    private static final java.util.Set<String> INDEXABLE_STATUSES = java.util.Set.of("RECRUITING", "IN_PROGRESS");

    private final ProjectSearchDocumentRepository repository;
    private final ObjectMapper objectMapper;
    private final SuggestionService suggestionService;
    private final DiscoveryMetrics metrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW) // <--- FIX
    public void applyDiscoverySync(ProjectDiscoveryUpdatedEvent event) {
        log.info("DIAG: applyDiscoverySync running for project {}", event.projectId());
        ProjectSearchDocument doc = repository.findById(event.projectId())
                .orElseGet(ProjectSearchDocument::new);

        doc.setProjectId(event.projectId());
        doc.setLeadUserId(event.leadUserId());
        doc.setTitle(event.title());
        doc.setSlug(event.slug());
        doc.setDescriptionExcerpt(event.descriptionExcerpt());
        doc.setCategory(event.category());
        doc.setVisibility(event.visibility());
        doc.setStatus(event.status());
        doc.setRequiredSkillsJson(toJson(event.requiredSkills()));
        doc.setTagsJson(toJson(event.tags()));
        doc.setMaxTeamSize(event.maxTeamSize());
        doc.setCurrentTeamSize(event.currentTeamSize());
        doc.setIndexed(INDEXABLE_STATUSES.contains(event.status()));
        if (doc.getPublishedAt() == null) {
            doc.setPublishedAt(event.occurredAt());
        }
        doc.setPopularityScore(computePopularityBaseline(doc));

        repository.save(doc);
        log.info("DIAG: discovery doc saved, projectId={}, indexed={}", doc.getProjectId(), doc.isIndexed());
        metrics.recordProjectionLag("project", java.time.Duration.between(event.occurredAt(), Instant.now()).toMillis());
        suggestionService.recordTerm(event.title(), SuggestionEntityType.PROJECT_TITLE);
        event.requiredSkills().forEach(skill -> suggestionService.recordTerm(skill, SuggestionEntityType.SKILL));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // <--- FIX
    public void applyPublished(ProjectPublishedEvent event) {
        repository.findById(event.projectId()).ifPresent(doc -> {
            if (doc.getPublishedAt() == null) {
                doc.setPublishedAt(event.occurredAt());
                repository.save(doc);
                log.info("DIAG: discovery doc saved, projectId={}, indexed={}", doc.getProjectId(), doc.isIndexed());
            }
        });
        if (repository.findById(event.projectId()).isEmpty()) {
            log.debug("Discovery: ProjectPublishedEvent for {} arrived before its DiscoveryUpdated sync event.",
                    event.projectId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // <--- FIX
    public void applyArchived(ProjectArchivedEvent event) {
        repository.findById(event.projectId()).ifPresent(doc -> {
            doc.setIndexed(false);
            doc.setStatus("ARCHIVED");
            repository.save(doc);
            log.info("DIAG: discovery doc saved, projectId={}, indexed={}", doc.getProjectId(), doc.isIndexed());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // <--- FIX
    public void applyCompleted(ProjectCompletedEvent event) {
        repository.findById(event.projectId()).ifPresent(doc -> {
            doc.setIndexed(false);
            doc.setStatus("COMPLETED");
            repository.save(doc);
            log.info("DIAG: discovery doc saved, projectId={}, indexed={}", doc.getProjectId(), doc.isIndexed());
        });
    }

    /**
     * A cheap, always-available freshness+fill-ratio proxy for "popularity"
     * until Discovery has real engagement signals (views, application
     * counts) to draw on -- none are published as events today. Documented
     * as an explicit extension point: swap this for a real signal the
     * moment one exists, without changing the ranking rule that reads it.
     */
    private double computePopularityBaseline(ProjectSearchDocument doc) {
        double fillRatio = doc.getMaxTeamSize() > 0
                ? (double) doc.getCurrentTeamSize() / doc.getMaxTeamSize()
                : 0;
        long daysOld = doc.getPublishedAt() == null ? 0 :
                java.time.Duration.between(doc.getPublishedAt(), Instant.now()).toDays();
        double freshnessBoost = Math.max(0, 30 - daysOld) / 30.0;
        return (fillRatio * 0.6) + (freshnessBoost * 0.4);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Discovery: failed to serialize projection field, defaulting to empty array.", e);
            return "[]";
        }
    }
}